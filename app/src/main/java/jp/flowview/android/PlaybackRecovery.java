package jp.flowview.android;

/** Bounded recovery for a transition, cancelled immediately by explicit controls or focus loss. */
final class PlaybackRecovery {
    private int generation,attempts;
    private long deadline;
    private boolean resume;
    int begin(long now,boolean wasPlaying){generation++;attempts=0;deadline=now+2200;resume=wasPlaying;return generation;}
    void cancel(){generation++;resume=false;}
    boolean takeAttempt(int token,long now,boolean playing){
        if(token!=generation||!resume||playing||now>deadline||attempts>=2)return false;
        attempts++;return true;
    }
}
