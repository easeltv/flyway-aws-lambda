package easeltv.flywayawslambda.deploy

import java.nio.file.{Files, Path, Paths}
import java.util.{Properties => JProperties}

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3ObjectSummary
import easeltv.flywayawslambda.MigrationRequest

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try

class S3SourceFlywayDeployer(s3Client: AmazonS3, migrationRequest: MigrationRequest) {

  def deploy(implicit context: Context): Try[FlywayDeployment] = Try {
    val logger = context.getLogger

    val tmpDir = Files.createDirectories(Paths.get("/tmp", context.getAwsRequestId))
    Files.createDirectories(Paths.get(tmpDir.toString, migrationRequest.s3Prefix))

    @tailrec
    def deployInternal(objects: List[S3ObjectSummary], acc: ListBuffer[Path]): (Seq[Path]) = {
      def createDir(key: String) = {
        val dir = Files.createDirectories(Paths.get(tmpDir.toString, key))
        logger.log(s"Dir created. $dir")
        acc
      }
      def createSqlFile(key: String) = {
        val o = s3Client.getObject(migrationRequest.s3BucketName, key)
        val file = Paths.get(tmpDir.toString, key)
        val fileSize = Files.copy(o.getObjectContent, file)
        logger.log(s"SQL file created. $file($fileSize Byte)")
        acc += file
        acc
      }

      objects match {
        case Nil => acc
        case x :: xs =>
          val _acc = x.getKey match {
            case key if key.endsWith("/") => createDir(key)
            case key if key.endsWith(".sql") => createSqlFile(key)
            case _ => acc
          }
          deployInternal(xs, _acc)
      }
    }

    val objectSummaries = {
      val objects = s3Client.listObjects(migrationRequest.s3BucketName, migrationRequest.s3Prefix)
      objects.getObjectSummaries.asScala.toList.sortWith { (x, y) =>
        x.getKey.compareTo(y.getKey) < 1
      }
    }

    logger.log(s"Deploying Flyway resources from ${migrationRequest.s3BucketName}/${migrationRequest.s3Prefix}... ${objectSummaries.map(_.getKey).mkString(", ")}")

    deployInternal(objectSummaries, ListBuffer()) match {
      case sqlFiles =>
        FlywayDeployment(
          migrationRequest,
          s"filesystem:${Paths.get(tmpDir.toString, migrationRequest.s3Prefix).toString}",
          sqlFiles,
          context
        )
    }
  }

}
