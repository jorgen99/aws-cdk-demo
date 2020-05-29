A Sandbox playground that I'm using to learn aws cdk
https://aws.amazon.com/cdk/

This project was originally generated with:

    cdk init --language java

At the moment the created stack is running a Spring Boot docker container
created in this project: https://github.com/jorgen99/spring-boot-aws-docker-demo

It runs on https://www.jorgenlundberg.com and has automatic forwarding
of http -> https.

The https certificate is stored in AWS Certificate Manager.

- DONE: Docker spring-boot REST service running in fargate.
- DONE: http forwarding to http
- DONE: S3 bucket with lambda event handler
- DONE: lambda calling spring boot REST service
- DONE: rest service saving info to DynamoDB
