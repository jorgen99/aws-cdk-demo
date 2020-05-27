package com.myorg.lambda;

import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;

public class S3FileEventLambda implements RequestHandler<S3Event, String> {

  public String handleRequest(S3Event s3event, Context context) {
    List<S3EventNotification.S3EventNotificationRecord> records = s3event.getRecords();
    S3EventNotification.S3EventNotificationRecord record = records.get(0);

    String srcBucket = record.getS3().getBucket().getName();

    // Object key may have spaces or unicode non-ASCII characters.
    String srcKey = record.getS3().getObject().getUrlDecodedKey();

    String msg = "Found new file: '" + srcKey + "', in bucket: '" + srcBucket + "'";
    System.out.println(msg);
    String serviceUrl = System.getenv("SERVICE_URL");
    System.out.println("Calling the service on url: '" + serviceUrl + "'");
    return msg;
  }
}
