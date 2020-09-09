package easeltv.flywayawslambda

import java.io.{BufferedOutputStream, InputStream, OutputStream, PrintWriter}

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

import scala.io.{BufferedSource, Codec}
import scala.util.{Failure, Success, Try}

class InvokeMigrationHandler extends RequestStreamHandler with S3MigrationHandlerBase {
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    def parseInput: Try[MigrationRequest] = {
      Try {
        import spray.json._
        import DefaultJsonProtocol._

        val json = new BufferedSource(input)(Codec("UTF-8")).mkString
        val jsObj = JsonParser(json).toJson.asJsObject
        jsObj.getFields(
          "s3BucketName",
          "s3Prefix",
          "databaseUrl",
          "databaseUsername",
          "databasePassword",
          "schema",
          "outOfOrder",
          "cleanOnValidationError"
        ) match {
          case Seq(
              JsString(s3BucketName),
              JsString(s3Prefix),
              JsString(databaseUrl),
              JsString(databaseUsername),
              JsString(databasePassword),
              JsString(schema),
              JsBoolean(outOfOrder),
              JsBoolean(cleanOnValidationError
            )) => MigrationRequest(s3BucketName, s3Prefix, databaseUrl, databaseUsername, databasePassword, schema, outOfOrder, cleanOnValidationError)
          case _ => throw new IllegalArgumentException(s"Missing require key [s3BucketName, s3Prefix, databaseUrl, databaseUsername, databasePassword, schema]. - $json")
        }
      }
    }

    val logger = context.getLogger

    val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard().withRegion(Regions.fromName(sys.env("AWS_REGION"))).build()

    (for {
      migrationRequest <- parseInput
      _ = { logger.log(s"Flyway migration start. by invoke lambda function $migrationRequest.") }
      r <- migrate(migrationRequest)(context, s3Client)
    } yield r) match {
      case Success(r) =>
        logger.log(r)
        val b = r.getBytes("UTF-8")
        val bout = new BufferedOutputStream(output)
        Stream.continually(bout.write(b))
        bout.flush()
      case Failure(e) =>
        e.printStackTrace()
        val w = new PrintWriter(output)
        w.write(e.toString)
        w.flush()
    }
  }

}