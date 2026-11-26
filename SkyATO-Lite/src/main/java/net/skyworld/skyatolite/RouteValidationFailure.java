package net.skyworld.skyatolite;

import java.util.Objects;
import java.util.function.Supplier;
import net.skyworld.sta.api.v10.RouteEditingService.ValidationIssue;

/** Attach the actual directed visit pair without continuing from an unknown arrival. */
final class RouteValidationFailure extends IllegalArgumentException {
    final ValidationIssue issue;
    RouteValidationFailure(int from,int to,String fromId,String toId,String fromLabel,String toLabel,IllegalArgumentException cause){
        super("#"+from+" "+fromLabel+" → #"+to+" "+toLabel+"："+Objects.toString(cause.getMessage(),"验证失败"),cause);
        issue=new ValidationIssue(from,to,fromId,toId,fromLabel,toLabel,Objects.toString(cause.getMessage(),"验证失败"));
    }
    static <T> T at(int from,int to,String fromId,String toId,String fromLabel,String toLabel,Supplier<T> operation){
        try{return operation.get();}catch(RouteValidationFailure ex){throw ex;}catch(IllegalArgumentException ex){throw new RouteValidationFailure(from,to,fromId,toId,fromLabel,toLabel,ex);}
    }
}
