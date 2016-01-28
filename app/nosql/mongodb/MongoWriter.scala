package nosql.mongodb

import nosql.FeatureWriter
import play.api.Logger
import scala.concurrent._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import nosql.mongodb.MongoDBRepository.CollectionInfo


import org.reactivemongo.play.json._


/**
 * @author Karel Maesen, Geovise BVBA
 *         creation-date: 9/13/13
 */

case class MongoWriter(db: String, collection: String) extends FeatureWriter {

  import config.AppExecutionContexts.streamContext

  val fCollectionInfo: Future[CollectionInfo] = MongoDBRepository.getCollectionInfo(db, collection)

  def add(features: Seq[JsObject]) : Future[Long] =
    if(features.isEmpty) Future.successful(0)
    else
      fCollectionInfo.flatMap{ case (coll, smd, transfo) => {
        val docs = features.map(f => f.transform(transfo)).collect {
          case JsSuccess(transformed, _) => transformed
        }.map(implicitly[coll.ImplicitlyDocumentProducer](_)) //for this last map see: https://groups.google.com/forum/#!topic/reactivemongo/dslaGJfxd6s

        Logger.debug(" Flushing data to mongodb (" + docs.size + " features)")
        val fInt = coll.bulkInsert(true)( docs:_* ).map(i => i.n.toLong)

        fInt.onSuccess {
          case num => Logger.info(s"Successfully inserted $num features")
        }

        fInt.recover {
          case t : Throwable => { Logger.warn(s"Insert failed with error: ${t.getMessage}"); 0L }
        }
      }
    }

}