package com.myorg.lambda;

import java.io.IOException;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;

@SuppressWarnings("unused")
public class S3FileEventLambda implements RequestHandler<S3Event, String> {

  public String handleRequest(S3Event s3event, Context context) {
    List<S3EventNotification.S3EventNotificationRecord> records = s3event.getRecords();
    S3EventNotification.S3EventNotificationRecord record = records.get(0);

    String srcBucket = record.getS3().getBucket().getName();
    // Object key may have spaces or unicode non-ASCII characters.
    String s3Key = record.getS3().getObject().getUrlDecodedKey();
    String msg = "Found new file: '" + s3Key + "', in bucket: '" + srcBucket + "'";
    System.out.println(msg);
    FileEvent fileEvent = new FileEvent(s3Key);

    String serviceHost = System.getenv("SERVICE_URL");
    String serviceUrl = "https://www.jorgenlundberg.com/event/v1";
    System.out.println("Posting to the service on url: '" + serviceUrl + "'");
    pokeSpringBootService(serviceUrl, fileEvent.asJsong());
    return msg;
  }

  private void pokeSpringBootService(String url, String jsonPayload) {

    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpPost httppost = new HttpPost(url);
      HttpEntity e = new StringEntity(jsonPayload);
      httppost.setEntity(e);
      httppost.addHeader("Accept", "application/json");
      httppost.addHeader("Content-Type", "application/json");

      System.out.println("Executing request: " + httppost.getRequestLine());

      try (CloseableHttpResponse response = httpclient.execute(httppost)) {
        System.out.println("----------------------------------------");
        System.out.println(response.getStatusLine());
        System.out.println(EntityUtils.toString(response.getEntity()));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static class FileEvent {
    private final String s3Key;

    FileEvent(String s3Key) {
      this.s3Key = s3Key;
    }

    String asJsong() {
      return String.format("{ \"s3Key\": \"%s\" }", s3Key);
    }
  }

}
