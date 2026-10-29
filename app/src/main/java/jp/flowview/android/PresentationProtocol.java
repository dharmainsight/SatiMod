package jp.flowview.android;

/** Native commands own presentation. Page acknowledgements never become new commands. */
final class PresentationProtocol {
    private String mode="inline";
    private long revision;
    String mode(){return mode;}
    long revision(){return revision;}
    boolean accepts(long value){return value==revision;}
    boolean change(String value){
        if(!"inline".equals(value)&&!"full".equals(value)&&!"pip".equals(value))return false;
        if(mode.equals(value))return false;
        mode=value;revision++;return true;
    }
}
