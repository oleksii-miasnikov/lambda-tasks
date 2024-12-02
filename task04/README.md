# task04

High level project overview - business value it brings, non-detailed technical overview.

### Notice
All the technical details described below are actual for the particular
version, or a range of versions of the software.
### Actual for versions: 1.0.0

## task04 diagram

![task04](pics/task04_diagram.png)

## Lambdas descriptions

### Lambda `lambda-name`
Lambda feature overview.

### Required configuration
#### Environment variables
* environment_variable_name: description

#### Trigger event
```buildoutcfg
{
    "key": "value",
    "key1": "value1",
    "key2": "value3"
}
```
* key: [Required] description of key
* key1: description of key1

#### Expected response
```buildoutcfg
{
    "status": 200,
    "message": "Operation succeeded"
}
```
---

## Deployment from scratch
1. action 1 to deploy the software
2. action 2
...

1. Generate Project:

   syndicate generate project --name task04

2. Generate Config:


3. Set up the SDCT_CONF environment variable pointing to the folder with syndicate.yml file

setx SDCT_CONF C:\WORK\LABA\lambda-tasks\task04\.syndicate-config-dev

4. Generate Lambda:

syndicate generate lambda --name sqs_handler --runtime java
syndicate generate lambda --name sns_handler --runtime java

5. Generate SQS Queue Resource in Meta:

syndicate generate meta sqs_queue --resource_name async_queue --region eu-central-1 --receive_message_wait_time_seconds 20


--dead_letter_target_arn arn:aws:sqs:eu-central-1:905418349556:cmtr-024ba94e-dead-letter-queue --max_receive_count 2

syndicate generate meta iam_role --resource_name sqs_handler-role --principal_service lambda --predefined_policies AWSLambdaSQSQueueExecutionRole  --custom_policies lambda-basic-execution

aws sqs send-message --queue-url https://sqs.eu-central-1.amazonaws.com/905418349556/cmtr-024ba94e-async_queue --message-body "Test"



syndicate generate meta sns_topic --resource_name lambda_topic --region eu-central-1 