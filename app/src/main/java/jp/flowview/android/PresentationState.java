package jp.flowview.android;

/** User viewing mode survives transient browser fullscreen exits during Android PiP. */
final class PresentationState {
    private boolean fullscreen,pip,entering,restoring,returnFullscreen;
    boolean fullscreen(){return fullscreen;}
    boolean protectedTransition(){return pip||entering||restoring;}
    boolean inPipOrEntering(){return pip||entering;}
    boolean videoOnly(){return fullscreen||pip||entering;}
    String pageMode(){return pip||entering?"pip":fullscreen?"full":"inline";}
    void pageFullscreen(boolean value){if(protectedTransition())return;fullscreen=value;}
    void requestFullscreen(boolean value){fullscreen=value;if(!value){returnFullscreen=false;restoring=false;if(!pip)entering=false;}}
    void beginPip(boolean returnToFullscreen){if(pip||entering)return;returnFullscreen=returnToFullscreen;entering=true;}
    void enteredPip(){pip=true;entering=false;}
    void leftPip(){if(!pip&&!entering)return;pip=false;entering=false;fullscreen=returnFullscreen;restoring=fullscreen;}
    void restored(String mode){if((fullscreen&&"full".equals(mode))||(!fullscreen&&"inline".equals(mode)))restoring=false;}
}
