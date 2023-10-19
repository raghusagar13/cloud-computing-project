package com.aws.ec2.rekognitiontext;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.QueueNameExistsException;
import com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.aws.ec2.rekognitiontext.config.AWSConfig;
import com.aws.ec2.rekognitiontext.service.QueueListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.jms.*;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class RekognitionTextApplication {
	private static final Logger logger = LoggerFactory.getLogger(RekognitionTextApplication.class);
	private static final String QUEUE_NAME = "Queue.fifo";

	public static void main(String[] args) throws JMSException {
		SpringApplication.run(RekognitionTextApplication.class, args);

		AWSConfig awsConfig = new AWSConfig();

		try {
			createQueueIfNotExists(awsConfig);

			SQSConnectionFactory connectionFactory = awsConfig.sqsConnectionFactory();
			SQSConnection connection = connectionFactory.createConnection();
			AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();
			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Queue queue = session.createQueue(QUEUE_NAME);
			MessageConsumer consumer = session.createConsumer(queue);
			consumer.setMessageListener(new QueueListener());
			connection.start();
			Thread.sleep(10000);

		} catch (AmazonServiceException | InterruptedException e) {
			logger.error("An error occurred: {}", e.getMessage());
		}
	}

	private static void createQueueIfNotExists(AWSConfig awsConfig) {
		try {
			SQSConnectionFactory connectionFactory = awsConfig.sqsConnectionFactory();
			SQSConnection connection = connectionFactory.createConnection();
			AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();
			if (!client.queueExists(QUEUE_NAME)) {
				Map<String, String> attributes = new HashMap<>();
				attributes.put("FifoQueue", "true");
				attributes.put("ContentBasedDeduplication", "true");
				client.createQueue(new CreateQueueRequest().withQueueName(QUEUE_NAME).withAttributes(attributes));
			}
			connection.close();
		} catch (QueueNameExistsException e) {
			logger.info("Queue already exists: {}", QUEUE_NAME);
		} catch (JMSException e) {
			logger.error("An error occurred while creating the queue: {}", e.getMessage());
		}
	}
}
