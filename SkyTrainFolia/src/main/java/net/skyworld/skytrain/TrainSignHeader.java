package net.skyworld.skytrain;

import java.util.Locale;

record TrainSignHeader(String name, boolean inverted, boolean always, boolean never,
        boolean rising, boolean falling, String directions, String remote) {
    static TrainSignHeader parse(String input) {
        String s=org.bukkit.ChatColor.stripColor(input==null?"":input).trim();
        if(!s.startsWith("[") || !s.endsWith("]")) return null;
        s=s.substring(1,s.length()-1).trim();
        boolean inv=false,on=false,off=false,rise=false,fall=false;
        int i=0;
        while(i<s.length() && "!+-/\\".indexOf(s.charAt(i))>=0) {
            switch(s.charAt(i++)) {case '!'->inv=true;case '+'->on=true;case '-'->off=true;case '/'->rise=true;case '\\'->fall=true;}
        }
        s=s.substring(i);
        int end=0;
        while(end<s.length() && s.charAt(end)!=':' && s.charAt(end)!=' ') end++;
        String name=s.substring(0,end).toLowerCase(Locale.ROOT);
        if(!name.equals("stf") && !name.equals("skytrain")) return null;
        String tail=s.substring(end).trim(), directions="",remote="";
        if(tail.startsWith(":")) directions=tail.substring(1).trim().toLowerCase(Locale.ROOT);
        else remote=tail;
        return new TrainSignHeader(name,inv,on,off,rise,fall,directions,remote);
    }
    boolean active(boolean powered) { return always || (!never && powered!=inverted); }
    boolean pulse() { return !always && !never && (rising || falling); }
    boolean poweredSide(boolean power) { return !never && (always || inverted!=power); }
    boolean activate(boolean powered, boolean previous, boolean edge) {
        if(pulse()) return edge && powered!=previous && ((powered&&rising)||(!powered&&falling)) && !inverted;
        return active(powered);
    }
    String display() {
        String prefix=always?"+":never?"-":(inverted?"!":"")+(rising?"/":"")+(falling?"\\":"");
        return "["+prefix+(name.equals("stf")?"STF":"SkyTrain")
                +(directions.isBlank()?remote.isBlank()?"":" "+remote:":"+directions)+"]";
    }
}
