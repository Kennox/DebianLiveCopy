package ch.fhnw.dlcopyfx.view;

import ch.fhnw.dlcopyfx.model.DlCopy;

public class DlCopyPresenter {
  private final DlCopy model;
  private final DlCopyView view;

  /** Javadoc comment here */
  public DlCopyPresenter(DlCopy model, DlCopyView view) {
    this.model = model;
    this.view = view;
    attachEvents();
  }

  private void attachEvents() {
    //TODO put all setOnAction/addListeners in here
  }
}
