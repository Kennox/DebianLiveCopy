
package ch.fhnw.dlcopyfx.view;

import ch.fhnw.dlcopy.DLCopy;


public class DLCopyView {
    private final DLCopy model;
    
    //declare all elements here
    
    public DLCopyView (DLCopy model) {
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


