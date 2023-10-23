package com.aws.ec2.rekognitiontext.service;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.*;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.aws.ec2.rekognitiontext.config.AWSConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.List;

public class QueueListener implements MessageListener {
    private static final Logger logger = LoggerFactory.getLogger(QueueListener.class);

    @Value("${BUCKET_NAME}")
    private static String BUCKET_NAME;


    @Override
    public void onMessage(Message message) {
        AWSConfig awsConfig = new AWSConfig();

        try {
            AmazonRekognition rekognitionClient = awsConfig.amazonRekognitionClient();
            String messageText = ((TextMessage) message).getText();

            ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(BUCKET_NAME);
            ListObjectsV2Result result = awsConfig.amazonS3Client().listObjectsV2(req);

            for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
                String photo = objectSummary.getKey();

                if (photo.contains(messageText)) {
                    DetectTextRequest request = new DetectTextRequest()
                            .withImage(new Image()
                                    .withS3Object(new S3Object()
                                            .withName(photo)
                                            .withBucket(BUCKET_NAME)));

                    try {
                        DetectTextResult result1 = rekognitionClient.detectText(request);
                        List<TextDetection> textDetections = result1.getTextDetections();

                        if (!textDetections.isEmpty()) {
                            logger.info("Text Detected lines and words for {}: ", photo);
                            for (TextDetection text : textDetections) {
                                logger.info("Text Detected: {}, Confidence: {}", text.getDetectedText(), text.getConfidence());
                            }
                        }
                    } catch (AmazonRekognitionException e) {
                        logger.error("Error: {}", e.getMessage());
                    }
                }
            }
        } catch (JMSException e) {
            logger.error("An error occurred: {}", e.getMessage());
        }
    }
}
