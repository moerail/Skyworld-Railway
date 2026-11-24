package net.skyworld.skyatolite;
import java.util.*;
/** A path change submits demand once; unchanged demand uses a silent, fenced heartbeat. */
final class AuthoritySession {
    private long revision=-1;private List<String> path=List.of();private boolean accepted;
    boolean needsSubmit(long r,List<String> p){return !accepted||revision!=r||!path.equals(p);}
    void accepted(long r,List<String> p){revision=r;path=List.copyOf(p);accepted=true;}
    List<String> path(){return path;}
    boolean active(){return accepted;}
    void reset(){accepted=false;}
}
