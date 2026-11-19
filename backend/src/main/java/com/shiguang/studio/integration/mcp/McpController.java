package com.shiguang.studio.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/mcp")
public class McpController {
  private final McpAuthorization auth; private final BusinessTools tools; private final ObjectMapper json; private final String token;
  public McpController(McpAuthorization auth,BusinessTools tools,ObjectMapper json,@Value("${studio.agent.internal-token:}")String token){this.auth=auth;this.tools=tools;this.json=json;this.token=token;}
  @PostMapping(produces=MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> post(@RequestBody JsonNode body,
      @RequestHeader(value="Authorization",required=false)String bearer,
      @RequestHeader(value="X-Agent-Token",defaultValue="")String internal,
      @RequestHeader(value="Origin",required=false)String origin){
    if(origin!=null || token.length()<32 || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),internal.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    var actor=auth.verify(bearer);
    if(!body.isObject()||!body.path("jsonrpc").asText().equals("2.0")||!body.path("method").isTextual())return error(body,-32600,"Invalid Request");
    String method=body.path("method").asText();
    if(!body.has("id"))return ResponseEntity.accepted().build();
    Object result;
    switch(method){
      case "initialize": result=Map.of("protocolVersion","2025-11-25","capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name","jianwei-business","version","1.0.0"));break;
      case "ping": result=Map.of();break;
      case "tools/list": result=Map.of("tools",tools.list(actor.role()));break;
      case "tools/call":
        var previous=SecurityContextHolder.getContext();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(Long.toString(actor.userId()),"",List.of(new SimpleGrantedAuthority("ROLE_"+actor.role()))));
        SecurityContextHolder.setContext(context);
        try {
          var data=tools.call(body.path("params").path("name").asText(),body.path("params").path("arguments"),actor);
          result=Map.of("content",List.of(Map.of("type","text","text",json.writeValueAsString(data))),"isError",false);
        }catch(IllegalArgumentException|java.time.DateTimeException ex){result=toolError("查询参数不正确或无权调用，请检查条件与权限。");}
        catch(ResponseStatusException ex){result=toolError(ex.getStatusCode().value()>=500?"业务服务暂不可用，请稍后重试。":"记录不存在、无权访问或查询条件不符合业务规则。");}
        catch(Exception ex){result=toolError("查询失败，请稍后重试；不能将失败当作没有记录。");}
        finally{SecurityContextHolder.setContext(previous);}
        break;
      default:return error(body,-32601,"Method not found");
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("jsonrpc","2.0","id",body.get("id"),"result",result));
  }
  private Object toolError(String text){return Map.of("content",List.of(Map.of("type","text","text",text)),"isError",true);}
  private ResponseEntity<?> error(JsonNode b,int code,String text){var value=new LinkedHashMap<String,Object>();value.put("jsonrpc","2.0");value.put("id",b.get("id"));value.put("error",Map.of("code",code,"message",text));return ResponseEntity.ok(value);}
  @GetMapping public ResponseEntity<Void> get(){return ResponseEntity.status(405).build();}
  @DeleteMapping public ResponseEntity<Void> delete(){return ResponseEntity.status(405).build();}
}
