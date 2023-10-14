package com.aws.ec2.rekognitionobject;

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

	public static void main(String[] args) throws IOException, JMSException, InterruptedException {
		SpringApplication.run(RekognitionObjectApplication.class, args);

	    Regions clientRegion = Regions.US_EAST_1;
		String bucketName = "awsobjecttextbucket";

		try {
			// Initialize an Amazon S3 client to work with your S3 bucket
			AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
					.withRegion(clientRegion)
					.build();

			// Create a new connection factory for Amazon SQS
			SQSConnectionFactory connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(),
					AmazonSQSClientBuilder.standard().withRegion(Regions.US_EAST_1));

			// Establish a connection to Amazon SQS
			SQSConnection connection = connectionFactory.createConnection();

			// Access the Amazon SQS client through a wrapper
			AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();

			// Create an Amazon SQS FIFO queue named queue.fifo if it doesn't already exist
			if (!client.queueExists("queue.fifo")) {
				// Configure queue attributes for FIFO and content-based deduplication
				Map<String, String> attributes = new HashMap<String, String>();
				attributes.put("FifoQueue", "true");
				attributes.put("ContentBasedDeduplication", "true");

				// Create the queue with specified attributes
				client.createQueue(new CreateQueueRequest().withQueueName("queue.fifo").withAttributes(attributes));
			}

			// Create a non-transacted session with AUTO_ACKNOWLEDGE mode for JMS
			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

			// Create a queue identity and specify the queue name
			Queue queue = session.createQueue("queue.fifo");

			// Create a producer to send messages to the 'MyQueue' FIFO queue
			MessageProducer producer = session.createProducer(queue);

			System.out.println("Listing objects in the S3 bucket...");

			// Set maxKeys to 2 to demonstrate the use of ListObjectsV2Result.getNextContinuationToken()
			ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName);
			ListObjectsV2Result result;

			do {
				// List objects in the S3 bucket
				result = s3Client.listObjectsV2(req);
				for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
					String photo = objectSummary.getKey();

					// Create an Amazon Rekognition client
					AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();

					// Prepare a request to detect labels in the image
					DetectLabelsRequest request = new DetectLabelsRequest()
							.withImage(new Image().withS3Object(new S3Object().withName(photo).withBucket(bucketName)))
							.withMaxLabels(10)
							.withMinConfidence(75F);

					try {
						// Detect labels in the image
						DetectLabelsResult result1 = rekognitionClient.detectLabels(request);
						List<Label> labels = result1.getLabels();

						// Create a data structure to hold detected labels and their confidence levels
						Hashtable<String, Integer> numbers = new Hashtable<String, Integer>();

						for (Label label : labels) {
							// Check if the detected label is "Car" with confidence greater than 90
							if (label.getName().equals("Car") && label.getConfidence() > 90) {
								System.out.print("Detected labels for:  " + photo + " => ");
								numbers.put(label.getName(), Math.round(label.getConfidence()));
								System.out.print("Label: " + label.getName() + " ,");
								System.out.print("Confidence: " + label.getConfidence().toString() + "\n");
								System.out.println("Pushed to SQS.");

								// Create a JMS message with the object's key and send it to the queue
								TextMessage message = session.createTextMessage(objectSummary.getKey());
								message.setStringProperty("JMSXGroupID", "Default");
								producer.send(message);

								System.out.println("JMS Message " + message.getJMSMessageID());
								System.out.println("JMS Message Sequence Number "
										+ message.getStringProperty("JMS_SQS_SequenceNumber"));
							}
						}
					} catch (AmazonRekognitionException e) {
						e.printStackTrace();
					}
				}
				// Get the next continuation token for pagination, if applicable
				String token = result.getNextContinuationToken();
				req.setContinuationToken(token);
			} while (result.isTruncated());
		} catch (AmazonServiceException e) {
			e.printStackTrace();
		} catch (SdkClientException e) {
			e.printStackTrace();
		}

	}

}
