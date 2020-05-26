package com.myorg;

import software.amazon.awscdk.core.App;

import java.util.Arrays;

public class CdkApp {
    public static void main(final String[] args) {
        App app = new App();

        new CdkStack(app, "CdkStack");

        app.synth();
    }
}
