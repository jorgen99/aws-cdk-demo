package com.myorg;

import static java.util.Collections.singletonList;
import static software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate.fromArn;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.CfnOutputProps;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterProps;
import software.amazon.awscdk.services.ecs.EcrImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateServiceProps;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddFixedResponseProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.S3EventSource;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.EventType;

public class CdkStack extends Stack {

  public static final String dockerRepoArn
      = "arn:aws:ecr:eu-north-1:534681061886:repository/jorgenlundberg/resttest";
  private static final String certificateArn
      = "arn:aws:acm:eu-north-1:534681061886:certificate/0a7fdd32-fc73-4889-bf4f-07727d0ee52a";

  public static final String prefix = "FooBar";
  public static final String vpcName = prefix + "Vpc";
  public static final String clusterName = prefix + "Cluster";
  public static final String serviceName = prefix + "FargateService";
  Stack stack;

  @NotNull ListenerCertificate certificate = fromArn(certificateArn);

  public CdkStack(final Construct scope, final String id) {
    this(scope, id, null);
  }

  public CdkStack(final Construct scope, final String id, final StackProps props) {
    super(scope, id, props);

    this.stack = Stack.of(this);

    Vpc vpc = new Vpc(
        this, vpcName,
        VpcProps.builder()
            .maxAzs(3)
            .build());

    Cluster cluster = new Cluster(
        this, clusterName,
        ClusterProps.builder()
            .vpc(vpc)
            .build());

    String repoId = prefix + "Image";
    IRepository repo = Repository.fromRepositoryArn(this, repoId, dockerRepoArn);
    EcrImage dockerImage = EcrImage.fromEcrRepository(repo);

    ApplicationLoadBalancer myLoadBalancer = new ApplicationLoadBalancer(
        this,
        "my_load_balancer",
        ApplicationLoadBalancerProps.builder()
            .vpc(cluster.getVpc())
            .internetFacing(true)
            .build()
    );
    ApplicationListener httpListener = myLoadBalancer
        .addListener(
            "my_http_listener",
            BaseApplicationListenerProps.builder()
                .protocol(ApplicationProtocol.HTTP)
                .build());

    // Dummy response. If omitted, we get the following error on `cdk synth`:
    // "Listener needs at least one default target group (call addTargetGroups)"
    // See: https://github.com/aws/aws-cdk/issues/2563 for details
    httpListener.addFixedResponse(
        "DummyResponse",
        AddFixedResponseProps.builder()
            .statusCode("404")
            .build());

    // Make the HTTP listener immediately redirect to HTTPS
    CfnListener cfnListener = (CfnListener) httpListener.getNode().getDefaultChild();
    cfnListener.setDefaultActions(
        singletonList(
            CfnListener.ActionProperty.builder()
                .type("redirect")
                .redirectConfig(
                    CfnListener.RedirectConfigProperty.builder()
                        .protocol("HTTPS")
                        .host("#{host}")
                        .path("/#{path}")
                        .query("#{query}")
                        .port("443")
                        .statusCode("HTTP_301")
                        .build()
                )
                .build()
        ));

    ApplicationListener httpsListener = myLoadBalancer.addListener(
        "my_https_listener",
        BaseApplicationListenerProps.builder()
            .protocol(ApplicationProtocol.HTTPS)
            .port(443)
            .build()
    );
    httpsListener.addCertificates(
        "my_list_of_https_certificates",
        singletonList(certificate)
    );

    ApplicationLoadBalancedFargateService theService =
        new ApplicationLoadBalancedFargateService(
            this,
            serviceName,
            ApplicationLoadBalancedFargateServiceProps.builder()
                .cluster(cluster)
                .cpu(256) // default 256
                .desiredCount(1) // default 1
                .taskImageOptions(
                    ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(dockerImage)
                        .containerPort(8080)
                        .build()
                )
                .memoryLimitMiB(2048) // default 512
                .publicLoadBalancer(true) // default false

                // Add a dummy listener to keep the service happy.
                // If we use any of the 'wanted' ports here, we get less control
                // of those listeners, since the service takes over and does its magic on it.
                // TODO: Find a cleaner solution where we don't have to add a dummy listener! (may require changes to CDK)
                .listenerPort(9999) // default 80

                // By default, the service instantiates a load balancer, but we want to pass our own.
                .loadBalancer(myLoadBalancer)
                .build()
        );

    // We need the HTTPS listener to use the same target group as the service,
    // so we set this here, after the service has been created
    httpsListener.addTargetGroups(
        "fargate_service_https_target_groups",
        AddApplicationTargetGroupsProps.builder()
            .targetGroups(singletonList(theService.getTargetGroup()))
            .build());

    Bucket fileEventBucket = Bucket.Builder
        .create(this, "the_file_event_bucket")
        .bucketName("jorgenlundberg-cdk-file-event-bucket")
        .build();


    Map<String, String> lambdaEnvMap = new HashMap<>();
    lambdaEnvMap.put("SERVICE_URL", theService.getLoadBalancer().getLoadBalancerDnsName());
    Function fileEventFunction = new Function(this, "file-event-lambda",
        getLambdaFunctionProps(lambdaEnvMap, "com.myorg.lambda.S3FileEventLambda"));

    fileEventFunction.addEventSource(
        S3EventSource.Builder
            .create(fileEventBucket)
            .events(singletonList(EventType.OBJECT_CREATED))
            //.filters()
        .build());

    //Outputs
    new CfnOutput(
        this,
        "LoadBalancerArn",
        CfnOutputProps.builder()
            .description("The ARN of the load balancer")
            .value(theService.getLoadBalancer().getLoadBalancerArn())
            .build()
    );

  }

  private FunctionProps getLambdaFunctionProps(Map<String, String> lambdaEnvMap, String handler) {
    return FunctionProps.builder()
        .code(Code.fromAsset("./asset/lambda-0.1-jar-with-dependencies.jar"))
        .handler(handler)
        .runtime(Runtime.JAVA_11)
        .environment(lambdaEnvMap)
        .timeout(Duration.seconds(30))
        .memorySize(512)
        .build();
  }

}
