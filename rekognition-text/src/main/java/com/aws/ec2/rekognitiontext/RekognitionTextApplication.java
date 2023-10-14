package com.aws.ec2.rekognitiontext;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.amazon.sqs.javamessaging.*;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.*;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.sqs.*;
import com.amazonaws.services.sqs.model.CreateQueueRequest;

import java.util.*;
import com.amazonaws.services.rekognition.model.*;
import javax.jms.*;
import javax.jms.Queue;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
@SpringBootApplication

class QueueListener implements MessageListener {

	@Override
	public void onMessage(Message message) {

		try {
			// Regions clientRegion = Regions.US_EAST_1;
			String clientRegion = "us-east-1d";
			String bucketName = "awsobjecttextbucket";

			ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName);
			ListObjectsV2Result result;

			AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
					.withRegion(clientRegion)
					.build();

			AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.standard()
					.withRegion(clientRegion)
					.build();

			result = s3Client.listObjectsV2(req);
			for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
				String m = (String) ((TextMessage) message).getText().toString();
				if (objectSummary.getKey().contains(m)) {
					// System.out.println("Received: " + ((TextMessage) message).getText());
					String photo = objectSummary.getKey();
					// text rekognition of the image from the queue
					DetectTextRequest request = new DetectTextRequest()
							.withImage(new Image()
									.withS3Object(new S3Object()
											.withName(photo)
											.withBucket(bucketName)));
					try {
						DetectTextResult result1 = rekognitionClient.detectText(request);
						List<TextDetection> textDetections = result1.getTextDetections();
						if (!textDetections.isEmpty()) {
							System.out.print("Text Detected lines and words for:  " + photo + " ==> ");
							for (TextDetection text : textDetections) {

								System.out.print("  Text Detected: " + text.getDetectedText() + " , Confidence: "
										+ text.getConfidence().toString());
								System.out.println();
							}
						}
					} catch (AmazonRekognitionException e) {
						System.out.print("Error");
						e.printStackTrace();
					}
				}
			}

		} catch (JMSException e) {
			System.out.println("Please run the Instance-1 first...");
		}
	}
}
public class RekognitionTextApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(RekognitionTextApplication.class, args);

		Regions clientRegion = Regions.US_EAST_1;

		try {
			// Create an Amazon SQS client for the specified region
			AmazonSQSClientBuilder.standard()
					.withRegion(clientRegion)
					.build();

			// Attempt to create an SQS queue; if it doesn't exist, it will wait for "Instance 2" to start
			try {
				// Create a new connection factory with all defaults (credentials and region set automatically)
				SQSConnectionFactory connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(),
						AmazonSQSClientBuilder.defaultClient());

				// Create a connection to Amazon SQS
				SQSConnection connection = connectionFactory.createConnection();
				// Get the wrapped Amazon SQS client
				AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();

				// Check if the queue named "queue.fifo" exists; if not, create it with specific attributes
				if (!client.queueExists("queue.fifo")) {
					Map<String, String> attributes = new HashMap<String, String>();
					attributes.put("FifoQueue", "true");
					attributes.put("ContentBasedDeduplication", "true");
					client.createQueue(
							new CreateQueueRequest().withQueueName("queue.fifo").withAttributes(attributes));
				}

				// Create a JMS session
				Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

				// Create a queue identity and specify the queue name
				Queue queue = session.createQueue("queue.fifo");

				// Create a JMS message consumer for the 'MyQueue'
				MessageConsumer consumer = session.createConsumer(queue);

				// Set the message listener for the consumer, which invokes the `QueueListener` class
				consumer.setMessageListener(new QueueListener());

				// Start receiving incoming messages
				connection.start();

				// Wait for 10 seconds (10,000 milliseconds)
				Thread.sleep(10000);

			} catch (Exception e) {
				System.out.println("Please run 'Instance-1'; the program will wait for the queue to have elements.");
				SQSConnectionFactory connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(),
						AmazonSQSClientBuilder.defaultClient());

				// Create a connection to Amazon SQS
				SQSConnection connection = connectionFactory.createConnection();
				// Get the wrapped Amazon SQS client
				AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();

				// Check if the queue named "queue.fifo" exists; if not, create it with specific attributes
				if (!client.queueExists("queue.fifo")) {
					Map<String, String> attributes = new HashMap<String, String>();
					attributes.put("FifoQueue", "true");
					attributes.put("ContentBasedDeduplication", "true");
					client.createQueue(
							new CreateQueueRequest().withQueueName("queue.fifo").withAttributes(attributes));

					Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
					// Create a queue identity and specify the queue name to the session
					Queue queue = session.createQueue("queue.fifo");

					// Create a JMS message consumer for the 'MyQueue'
					MessageConsumer consumer = session.createConsumer(queue);

					// Set the message listener for the consumer, which invokes the `QueueListener` class
					consumer.setMessageListener(new QueueListener());

					// Start receiving incoming messages
					connection.start();

					// Wait for 10 seconds (10,000 milliseconds)
					Thread.sleep(10000);
				}
			}

			// Wait for 1 second. The `onMessage()` method is invoked when a message is received.
		} catch (AmazonServiceException e) {
			System.out.println("Please run 'Instance-1' first. Waiting...");
		}
	}

}
