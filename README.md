# cloud-computing-project

# Application Setup and Cloud Environment Configuration

This README provides step-by-step instructions for setting up a cloud environment and running the application.

## Prerequisites
- You should have an active cloud provider account (e.g., AWS).

## Step 1: Create a User Role with Group Rights

1.1. Open your cloud provider's management console.

1.2. Navigate to the Identity and Access Management (IAM) service.

1.3. Create a new IAM group (e.g., "AppGroup").

1.4. Add permissions to the group for S3, Rekognition, and SQS services.

1.5. Create a new IAM user (e.g., "AppUser") and add the user to the "AppGroup" you created.

1.6. Generate and securely store the access and secret access keys for "AppUser."

## Step 2: Create an S3 Bucket and Upload Car Images

2.1. Open your cloud provider's management console.

2.2. Navigate to the Simple Storage Service (S3) dashboard.

2.3. Create a new S3 bucket (e.g., "car-images-bucket").

2.4. Configure the bucket with default permissions, keeping it private by default.

2.5. Upload the card images to the S3 bucket.

## Step 3: Run the Application

3.1. Clone or download the application source code from the repository.

3.2. Open a terminal and navigate to the application directory.

3.3. Configure the application with the AWS credentials (access key and secret key) of "AppUser."

3.4. Build and run the application according to the provided instructions.

## Application Console

### Object Rekognition
[OBJ REKOGNITION](images/obj-reko.png)

### text Rekognition
[TEXT REKOGNITION](images/text-reko.png)

