package net.skyworld.skyatolite;

import java.util.*;
import java.util.function.*;
import net.skyworld.sta.api.v10.RouteEditingService.*;

/** SAL-owned persistent drafts/publications and two-sided edit fencing. Caller serialises and persists. */
final class RouteWorkspace {
    static final long LEASE_MILLIS=300_000;
    static final class Session {
        UUID token;String actor,source;long until;Definition working;
        Session(Request r,Definition draft,long now){token=UUID.randomUUID();actor=r.actor();source=r.source();working=draft;until=now+LEASE_MILLIS;}
    }
    static final class Entry {
        Definition draft,published;long revision=1,publishedVersion,validatedRevision=-1;boolean conflict,retired;
        Map<String,Session> sessions=new LinkedHashMap<>();
    }
    Map<String,Entry> entries=new LinkedHashMap<>();
    long sequence;
    transient Map<UUID,Map.Entry<Request,Reply>> receipts=new LinkedHashMap<>();
    void restart(){receipts=new LinkedHashMap<>();for(var e:entries.values())if(!e.conflict)e.sessions.clear();}
    List<Line> lines(){return entries.entrySet().stream().map(e->view(e.getKey(),e.getValue())).toList();}
    Line line(String name){var e=entries.get(name);return e==null?null:view(name,e);}
    private Line view(String name,Entry e){return new Line(name,e.revision,e.publishedVersion,e.draft,e.published,
        e.validatedRevision==e.revision,e.retired,e.conflict,e.sessions.values().stream().map(s->new Participant(s.actor,s.source,s.working)).toList());}
    void importLegacy(Definition draft,boolean published){var e=new Entry();e.draft=draft;if(published&&!draft.depots().isEmpty()){e.published=draft;e.publishedVersion=1;}entries.put(draft.name(),e);}
    Reply apply(Request r,long now,Consumer<Definition> references,Consumer<Definition> validate,BooleanSupplier inUse,long graphRevision){
        var old=receipts.get(r.requestId());
        if(old!=null)return old.getKey().equals(r)?old.getValue():reply(r,"REJECTED","REQUEST_ID_CONFLICT",null,graphRevision);
        Reply result;
        try{result=process(r,now,references,validate,inUse,graphRevision);}
        catch(IllegalArgumentException|IllegalStateException ex){result=reply(r,"REJECTED",Objects.toString(ex.getMessage(),"INVALID_REQUEST"),null,graphRevision);}
        if(!r.action().equals("AUTH")&&(result.status().equals("APPLIED")||result.status().equals("CONFLICT")))sequence++;
        result=new Reply(result.requestId(),result.status(),result.reason(),result.session(),result.line(),result.graphRevision(),result.preview(),result.closed(),sequence);
        if(receipts.size()>=512)receipts.remove(receipts.keySet().iterator().next());
        receipts.put(r.requestId(),Map.entry(r,result));return result;
    }
    private Reply process(Request r,long now,Consumer<Definition> references,Consumer<Definition> validate,BooleanSupplier inUse,long graphRevision){
        if(r.action().equals("AUTH"))return reply(r,"APPLIED","AUTHENTICATED",null,graphRevision);
        var e=entries.get(r.line());
        if(r.action().equals("CREATE")){
            if(e!=null)throw new IllegalArgumentException("LINE_EXISTS");
            if(entries.size()>=512)throw new IllegalArgumentException("LINE_LIMIT");
            requireDefinition(r);references.accept(r.definition());
            e=new Entry();e.draft=r.definition();var s=new Session(r,e.draft,now);e.sessions.put(r.actor(),s);entries.put(r.line(),e);
            return reply(r,"APPLIED","CREATED",s.token,graphRevision);
        }
        if(e==null)throw new IllegalArgumentException("LINE_NOT_FOUND");
        if(r.action().equals("RESOLVE")){
            if(!e.conflict)throw new IllegalArgumentException("NO_CONFLICT");
            if(r.expectedRevision()!=e.revision)throw new IllegalArgumentException("STALE_REVISION");
            if("DISCARD".equals(r.decision()))e.sessions.clear();
            else {var keep=e.sessions.get(r.decision());if(keep==null)throw new IllegalArgumentException("SELECT_PARTICIPANT");var adopted=new Session(r,keep.working,now);e.sessions.clear();e.sessions.put(r.actor(),adopted);}
            e.conflict=false;
            var own=e.sessions.get(r.actor());return reply(r,"APPLIED","RESOLVED",own==null?null:own.token,graphRevision);
        }
        if(e.conflict)return reply(r,"CONFLICT","EDIT_FROZEN",null,graphRevision);
        e.sessions.values().removeIf(s->now>s.until);
        if(r.action().equals("BEGIN")){
            var own=e.sessions.get(r.actor());
            if(own==null){own=new Session(r,e.draft,now);e.sessions.put(r.actor(),own);}
            own.until=now+LEASE_MILLIS;
            if(e.sessions.size()>1){e.conflict=true;return reply(r,"CONFLICT","CONCURRENT_EDIT",null,graphRevision);}
            return reply(r,"APPLIED","EDITING",own.token,graphRevision);
        }
        var session=e.sessions.get(r.actor());
        if(session==null||!session.token.equals(r.session())||!session.source.equals(r.source()))throw new IllegalArgumentException("EDIT_SESSION_REQUIRED");
        session.until=now+LEASE_MILLIS;
        if(r.expectedRevision()!=e.revision){e.conflict=true;return reply(r,"CONFLICT","STALE_REVISION_FROZEN",null,graphRevision);}
        switch(r.action()){
            case "HEARTBEAT" -> {}
            case "UPDATE" -> {requireDefinition(r);references.accept(r.definition());session.working=r.definition();}
            case "SAVE" -> {references.accept(session.working);e.draft=session.working;e.revision++;e.validatedRevision=-1;}
            case "VALIDATE" -> {if(!session.working.equals(e.draft))throw new IllegalArgumentException("SAVE_FIRST");references.accept(e.draft);validate.accept(e.draft);e.validatedRevision=e.revision;}
            case "PUBLISH" -> {
                if(!session.working.equals(e.draft)||e.validatedRevision!=e.revision)throw new IllegalArgumentException("VALIDATE_FIRST");
                references.accept(e.draft);validate.accept(e.draft);e.published=e.draft;e.publishedVersion++;e.retired=false;
            }
            case "RETIRE" -> {e.retired=true;}
            case "DELETE" -> {if(inUse.getAsBoolean())throw new IllegalArgumentException("LINE_IN_USE");entries.remove(r.line());return reply(r,"APPLIED","DELETED",null,graphRevision);}
            case "CANCEL" -> {e.sessions.remove(r.actor());return reply(r,"APPLIED","EDIT_CLOSED",null,graphRevision);}
            default -> throw new IllegalArgumentException("UNKNOWN_ACTION");
        }
        return reply(r,"APPLIED",r.action(),session.token,graphRevision);
    }
    private void requireDefinition(Request r){if(r.definition()==null||!r.line().equals(r.definition().name()))throw new IllegalArgumentException("DEFINITION_REQUIRED");if(r.definition().depots().isEmpty())throw new IllegalArgumentException("DEPOT_REQUIRED");}
    private Reply reply(Request r,String status,String reason,UUID token,long graphRevision){return new Reply(r.requestId(),status,reason,token,line(r.line()),graphRevision,List.of(),false);}
}
