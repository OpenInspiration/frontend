package controllers

import model.Cached.RevalidatableResult
import model.{PreferencesMetaData, Cached}
import play.api.mvc.{Action, Controller}

class PreferencesController extends Controller with common.ExecutionContexts {

  def indexPrefs() = Action { implicit request =>
    Cached(300) {
      RevalidatableResult.Ok(views.html.preferences.index(new PreferencesMetaData()))
    }
  }
}

object PreferencesController extends PreferencesController
