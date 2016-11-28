package ch.fhnw.dlcopyfx.view;

import ch.fhnw.dlcopyfx.model.DlCopy;
import javafx.scene.Parent;

public class DlCopyView extends Parent { // change Parent to GridPane etc.
  private final DlCopy model;

  //declare all elements here

  /** Javadoc comment here. */
  public DlCopyView(DlCopy model) {
    this.model = model;

    layoutForm();
    initFieldData();
    bindFieldsToModel();
  }

  private void layoutForm() {
    //setup layout (aka setup specific pane etc.)
  }

  private void initFieldData() {
    //populate fields wich require initial data
  }

  private void bindFieldsToModel() {
    //make the bindings to the model
  }


}


