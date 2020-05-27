package com.myorg;

import java.io.IOException;
import java.util.Iterator;

import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.cxapi.CloudFormationStackArtifact;

public class CdkTest {
  private final static ObjectMapper JSON =
      new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

  @Ignore
  @Test
  public void testStack() throws IOException {
    App app = new App();
    CdkStack stack = new CdkStack(app, "test");

    // synthesize the stack to a CloudFormation template and compare against
    // a checked-in JSON file.
    CloudFormationStackArtifact stackArtifact = app
        .synth()
        .getStackArtifact(stack.getArtifactId());
    Object template = stackArtifact.getTemplate();
    JsonNode actual = JSON.valueToTree(template);
    Iterator<String> fieldNames = actual.get("Resources").fieldNames();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      System.out.println("fieldName = '" + fieldName + "'");
    }


    //System.out.println(actual.toPrettyString());
    //assertEquals(new ObjectMapper().createObjectNode(), actual);
  }
}
