package macros

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.collection.immutable.Seq
import scala.meta._

@compileTimeOnly("@Controller not expanded")
class Controller(main: String, primName: String, primType: String) extends StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    defn match {
      case Defn.Class(_, name, _, Ctor.Primary(_, _, paramss), template) =>
        // Extract macro's arguments
        val (main, primName, primType) = this match {
          case q"new $_(${Lit(main: String)}, ${Lit(primName: String)}, ${Lit(primType: String)})" => (main, primName, primType)
          case _                                                => ???
        }
        val primary = Term.Param(Seq(), Term.Name(primName), Some(Type.Name(primType)), None)
        val primaryName: Term.Arg = Term.Name(primary.name.value)

        val ret = q"""
           class $name @_root_.com.google.inject.Inject()
              (val repo: ${Type.Name(s"_root_.${Term.Name(s"${main.toLowerCase}s")}.${Term.Name(main)}.${main}Repository")},
               val database: _root_.accounts.model.AccountRepository,
               ws: _root_.play.api.libs.ws.WSClient,
               implicit val webJarAssets: _root_.controllers.WebJarAssets)
              extends _root_.play.api.mvc.Controller
                with _root_.accounts.AuthConfigTrait
                with _root_.jp.t2v.lab.play2.auth.AuthenticationElement {

             import play.api.libs.concurrent.Execution.Implicits.defaultContext
             import play.api.mvc._
             import play.api.libs.json.Json._
             import play.api.libs.json._
             import _root_.utils.Constants.{successResponse, failResponse}
             import _root_.scala.concurrent.Future
             import views.html

             val logger = _root_.play.api.Logger(this.getClass)

             def index = StackAction { implicit request =>
              Ok(html.index(loggedIn))
             }

             def list() = AsyncStack { implicit request =>
               repo.getAllByUser(loggedIn.name).map { res =>
                 logger.info("List: " + res)
                 Ok(successResponse(Json.toJson(res), "Got list successfully"))
               }
             }

             def create(): Action[JsValue] = AsyncStack(parse.json) { implicit request =>
               logger.info("Creating ===> " + request.body)
               _addUserToJson(request.body, loggedIn.name).validate[${Type.Name(main)}].fold(
                  error => Future.successful(BadRequest(JsError.toJson(error))),
                 { entity =>
                     repo.insert(entity).map { createdEntityId =>
                       Ok(successResponse(Json.toJson(Map("id" ->createdEntityId)), ${Lit(main)} + " has been created successfully."))
                     }
                 }
               )
             }


             def delete($primary) = AsyncStack { request =>
               repo.delete($primaryName).map { _ =>
                 Ok(successResponse(Json.toJson("{}"), ${Lit(main)} + " has been deleted successfully."))
               }
             }


             def edit($primary): Action[AnyContent] = AsyncStack { request =>
               repo.getById($primaryName).map { entityOpt =>
                 entityOpt.fold(Ok(failResponse(Json.obj(), ${Lit(main)} + " does not exist.")))(entity => Ok(
                   successResponse(Json.toJson(entity), "Got " + ${Lit(main)} + " successfully")))
               }
             }

             def update: Action[JsValue] = AsyncStack(parse.json) { implicit request =>
               logger.info("Updating ===> " + request.body)
               _addUserToJson(request.body, loggedIn.name).validate[${Type.Name(main)}].fold(
                 error => Future.successful(BadRequest(JsError.toJson(error))),
                 { entity =>
                     repo.update(entity).map { res =>
                       Ok(successResponse(Json.toJson("{}"), ${Lit(main)} + " has been updated successfully."))
                     }
                 }
               )
             }

             def infoSearch: Action[JsValue] = AsyncStack(parse.json) { implicit request =>
               logger.info("Searching for ===> " + request.body)
               _addUserToJson(request.body, loggedIn.name).validate[${Type.Name(main)}].fold(
                 error => Future.successful(BadRequest(JsError.toJson(error))),
                 entity => info(entity).map { information =>
                   Ok(successResponse(information, "Information has been fetched successfully"))
                 }
               )
             }

             def info(entity: Book): Future[JsValue] = {
               import play.api.libs.json._
               ws.url(s"https://www.googleapis.com/books/v1/volumes?q=intitle:$${entity.title}+inauthor:$${entity.author}&key=AIzaSyAP_-Rb-Hiw1C_fvOjzPBqLqttuJ-bspMA")
                 .get().map { response =>
                   val googleId: String = (response.json \\ "id").head.as[JsString].value
                   JsString(s"https://books.google.fr/books?id=$$googleId&printsec=frontcover&redir_esc=y")
                 }
             }

             def _addUserToJson(data: JsValue, userName: String): JsValue =
               data.as[JsObject] + ("accountId" -> Json.toJson(userName))
           }
         """


        println(ret)
        ret
      case _ =>
        abort("@Controller must annotate a class.")
    }
  }
}
