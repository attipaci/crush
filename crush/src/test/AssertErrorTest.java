package test;

import java.util.ArrayList;

public class AssertErrorTest {
    public static void main(String[] args) {       
        Instrument<?> myInstrument = getInstrument(args[0]); 
        myInstrument.getTest().run();
    }
    
    public static Instrument<?> getInstrument(String name) {
        if(name.equalsIgnoreCase("mine")) return new MyInstrument();
        return null;
    }
}




class Channel {
    int index;
}

class MyChannel extends Channel {   
}


class ChannelGroup<ChannelType extends Channel> extends ArrayList<ChannelType> {      
    
    public class Fork {
        public Fork() {}
        
        public void print(int index) { print(get(index)); }
        
        public void print(ChannelType channel) { System.out.println(channel.index); } 
    }
}
    



abstract class Instrument<ChannelType extends Channel> extends ChannelGroup<ChannelType> { 
    public ChannelGroup<ChannelType> getChannels() {
        ChannelGroup<ChannelType> channels = new ChannelGroup<ChannelType>();
        channels.addAll(this);
        return channels;
    }
    
    public abstract Test<?> getTest();
 
}

class MyInstrument extends Instrument<MyChannel> {
    public MyInstrument() {
        add(new MyChannel());
    }
    
    @Override
    public Test<?> getTest() {
        return new Test<MyInstrument>(this);
    }
}

class Test<InstrumentType extends Instrument<?>> {
    InstrumentType instrument;
    
    public Test(InstrumentType instrument) {
        this.instrument = instrument;
    }
    
    public void run() {
        ChannelGroup<?> channels = instrument.getChannels();
        channels.new Fork().print(0);
    }
}