package com.aws.ec2.rekognitiontext.config;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AWSConfig {

    @Bean
    public AmazonS3 amazonS3Client() {
        return AmazonS3ClientBuilder.defaultClient();
    }

    @Bean
    public AmazonRekognition amazonRekognitionClient() {
        return AmazonRekognitionClientBuilder.defaultClient();
    }

    @Bean
    public SQSConnectionFactory sqsConnectionFactory() {
        return new SQSConnectionFactory(new ProviderConfiguration(),
                AmazonSQSClientBuilder.defaultClient());
    }
}
