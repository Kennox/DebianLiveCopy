package ch.fhnw.dlcopyfx;

import ch.fhnw.dlcopyfx.model.DlCopy;
import ch.fhnw.dlcopyfx.view.DlCopyPresenter;
import ch.fhnw.dlcopyfx.view.DlCopyView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DlCopyApp extends Application {
  public static final String APP_NAME = "DLCopy";

  public static void main(String[] args) {
    Application.launch(args);
  }

  @Override
  public void start(Stage stage) {
    DlCopy model = new DlCopy();
    DlCopyView view = new DlCopyView(model);

    // Must set the scene before creating the presenter that uses
    // the scene to listen for the focus change
    Scene scene = new Scene(view);

    DlCopyPresenter presenter = new DlCopyPresenter(model, view);

    stage.setScene(scene);
    stage.setTitle(APP_NAME);
    stage.show();
  }
}
