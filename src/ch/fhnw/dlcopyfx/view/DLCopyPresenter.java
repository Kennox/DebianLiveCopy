
package ch.fhnw.dlcopyfx.view;

import ch.fhnw.dlcopy.DLCopy;

public class DLCopyPresenter {
    private final DLCopy model;
    private final DLCopyView view;
    
    public DLCopyPresenter (DLCopy model, DLCopyView view) {
        this.model = model;
        this.view = view;
        attachEvents();
    }
    
    private void attachEvents() {
        //TODO put all setOnAction/addListeners in here
    }
}
