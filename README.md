Flyway AWS Lambda function
===========================
 
# What's this?

Lambda function for AWS RDS Migration using [Flyway](https://flywaydb.org). Particularly useful for AWS RDS Aurora Serverless since it is inaccessible from outside a VPC.

An EC2 instance is not necessary for DB migration.

This Lambda function reads SQL files from an S3 bucket and is invoked by a REST API call.

### History
Based off https://github.com/crossroad0201/flyway-awslambda but updated to use more up to date dependencies and focused on triggering the lambda via REST.

# Setup

## S3 bucket

Create S3 bucket and folder for Flyway resources.
 
### Bucket structure

```
s3://my-flyway             <- Flyway migration bucket.
  - /my-application        <- Flyway resource folder(prefix).
    - V1__create_foo.sql   <- SQL file(s)
    - V2__create_bar.sql
```

## AWS Settings

### VPC Endpoint 

Require [VPC Endpoint](http://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/vpc-endpoints.html) for access to S3 Bucket from Lambda function in VPC.


## Deploy Lambda function

### Code

* Download jar module from releases
  * Or build from source.(Require JDK, Scala, sbt)
```
sbt assembly
```

* Upload `flyway-awslambda-x.x.x.jar`.

### Configuration

||value|
|----|----|
|Runtime|`Java 8`|
|Handler|`easeltv.flywayawslambda.InvokeMigrationHandler`|
|Role|See `Role` section.|
|Timeout|`5 min.`|
|VPC|Same VPC as target RDS.|

#### Role

Require policies.

* AmazonRDSFullAccess
* AmazonS3FullAccess
* AmazonLambdaVPCAccessExecutionRole

# Run

Put Flyway SQL file(s) into S3 resource folder.
 
And invoke flyway-lambda function yourself with the following json payload.

```json
{
  "s3BucketName": "my-flyway",
  "s3Prefix": "my-application",
  "databaseUrl": "jdbc:mysql://RDS_ENDPOINT/DATABSE_NAME",
  "databaseUsername": "USER_NAME",
  "databasePassword": "PASSWORD",
  "schema": "SCHEMA",
  "outOfOrder": true,
  "cleanOnValidationError": false
}
```

Check result message or `migration-result.json` in S3 resource folder for result,
and CloudWatch log for more detail.

#### Additional Supported options.
The following fields are passed to flyway.

* `schema`
* `outOfOrder`
* `cleanOnValidationError`

See [Flyway - Config Files](https://flywaydb.org/documentation/configfiles) for more details.