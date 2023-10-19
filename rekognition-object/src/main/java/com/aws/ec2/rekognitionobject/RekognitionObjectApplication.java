package com.aws.ec2.rekognitionobject;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.aws.ec2.rekognitionobject.config.AWSConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper;
import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
@SpringBootApplication
public class RekognitionObjectApplication {
	private static final String BUCKET_NAME = "reko-text-object";
	private static final String QUEUE_NAME = "Queue.fifo";

	private static final Logger logger = LoggerFactory.getLogger(RekognitionObjectApplication.class);

	public static void main(String[] args) throws IOException, JMSException, InterruptedException {
		SpringApplication.run(RekognitionObjectApplication.class, args);

		AWSConfig awsConfig = new AWSConfig();

		try {
			SQSConnectionFactory connectionFactory = awsConfig.sqsConnectionFactory();
			SQSConnection connection = connectionFactory.createConnection();
			AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();

			createQueueIfNotExists(client);

			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Queue queue = session.createQueue(QUEUE_NAME);
			MessageProducer producer = session.createProducer(queue);

			logger.info("Listing objects in the S3 bucket...");
			ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(BUCKET_NAME);
			ListObjectsV2Result result;

			do {
				result = awsConfig.amazonS3Client().listObjectsV2(req);
				for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
					processObject(awsConfig, objectSummary, session, producer);
				}
				String token = result.getNextContinuationToken();
				req.setContinuationToken(token);
			} while (result.isTruncated());
		} catch (SdkClientException e) {
			logger.error("An error occurred: {}", e.getMessage());
		}
	}

	private static void createQueueIfNotExists(AmazonSQSMessagingClientWrapper client) throws JMSException {
		if (!client.queueExists(QUEUE_NAME)) {
			Map<String, String> attributes = new HashMap<>();
			attributes.put("FifoQueue", "true");
			attributes.put("ContentBasedDeduplication", "true");
			client.createQueue(new CreateQueueRequest().withQueueName(QUEUE_NAME).withAttributes(attributes));
		}
	}

	private static void processObject(AWSConfig awsConfig, S3ObjectSummary objectSummary, Session session, MessageProducer producer) {
		String photo = objectSummary.getKey();

		AmazonRekognition rekognitionClient = awsConfig.amazonRekognitionClient();
		DetectLabelsRequest request = new DetectLabelsRequest()
				.withImage(new Image().withS3Object(new S3Object().withName(photo).withBucket(BUCKET_NAME)))
				.withMaxLabels(10)
				.withMinConfidence(75F);

		try {
			DetectLabelsResult result = rekognitionClient.detectLabels(request);
			List<Label> labels = result.getLabels();
			Hashtable<String, Integer> numbers = new Hashtable<String, Integer>();

			for (Label label : labels) {
				logger.info(String.valueOf(label));

				if (label.getName().equals("Car") && label.getConfidence() > 90) {
					numbers.put(label.getName(), Math.round(label.getConfidence()));
					logger.info("Detected label for {}: Label: {}, Confidence: {}", photo, label.getName(), label.getConfidence());
					logger.info("Object {} pushed to SQS.", photo);

					TextMessage message = session.createTextMessage(objectSummary.getKey());
					message.setStringProperty("JMSXGroupID", "Default");
					producer.send(message);

					logger.info("JMS Message ID: {}", message.getJMSMessageID());
					logger.info("JMS Sequence Number: {}", message.getStringProperty("JMS_SQS_SequenceNumber"));
				}
			}
		} catch (AmazonRekognitionException | JMSException e) {
			e.printStackTrace();
		}
	}
}
