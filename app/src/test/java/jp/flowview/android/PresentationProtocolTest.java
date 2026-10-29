package jp.flowview.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresentationProtocolTest {
    @Test public void lateFullscreenCannotOverrideANewerPortraitRequest(){
        PresentationProtocol p=new PresentationProtocol();p.change("full");long previous=p.revision();
        p.change("inline");assertFalse(p.accepts(previous));assertEquals("inline",p.mode());
        assertTrue(p.accepts(p.revision()));
    }
    @Test public void repeatedRotationAndAcknowledgementsDoNotCreateCommands(){
        PresentationProtocol p=new PresentationProtocol();assertTrue(p.change("full"));long revision=p.revision();
        for(int i=0;i<1000;i++){assertTrue(p.accepts(revision));assertFalse(p.change("full"));}
        assertEquals(revision,p.revision());assertEquals("full",p.mode());
    }
    @Test public void pipRoundTripRejectsQueuedRepliesFromBothPreviousModes(){
        PresentationProtocol p=new PresentationProtocol();p.change("full");long full=p.revision();
        p.change("pip");long pip=p.revision();p.change("full");
        assertFalse(p.accepts(full));assertFalse(p.accepts(pip));assertTrue(p.accepts(p.revision()));
    }
    @Test public void invalidModesAndMissingRevisionsAreRejected(){
        PresentationProtocol p=new PresentationProtocol();assertFalse(p.change(null));assertFalse(p.change("landscape"));
        assertFalse(p.accepts(-1));assertEquals("inline",p.mode());assertEquals(0,p.revision());
    }
}
