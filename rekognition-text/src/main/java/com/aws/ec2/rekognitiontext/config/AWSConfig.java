package com.aws.ec2.rekognitiontext.config;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AWSConfig {

    private String accessKeyId = "AKIA4OKLBQSLHNZMB35V";

    private String secretKey = "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";

    private Regions region = Regions.US_EAST_2;

    @Bean
    AWSCredentials awsCredentials() {
        return new BasicAWSCredentials(accessKeyId, secretKey);
    }

    @Bean
    public AmazonS3 amazonS3Client() {
        return AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials()))
                .build();
    }

    @Bean
    public AmazonRekognition amazonRekognitionClient() {
        return AmazonRekognitionClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials()))
                .build();
    }

    @Bean
    public SQSConnectionFactory sqsConnectionFactory() {
        return new SQSConnectionFactory(new ProviderConfiguration(),
                AmazonSQSClientBuilder.standard().withRegion(region)
                        .withCredentials(new AWSStaticCredentialsProvider(awsCredentials())));
    }
}
