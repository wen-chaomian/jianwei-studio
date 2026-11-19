package com.shiguang.studio.integration.mcp;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.studio.security.AccountStateCache;
import com.shiguang.studio.vo.UserVO;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.session.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
class McpAuthorizationTest {
  StringRedisTemplate redis=mock(StringRedisTemplate.class);
  ValueOperations<String,String> values=mock(ValueOperations.class);
  SessionRepository<MapSession> sessions=mock(SessionRepository.class);
  AccountStateCache accounts=mock(AccountStateCache.class);
  ObjectMapper json=new ObjectMapper();
  McpAuthorization auth=new McpAuthorization(redis,sessions,accounts,json);
  String token=UUID.randomUUID()+"-"+UUID.randomUUID();
  MapSession session=new MapSession();
  @BeforeEach void setup()throws Exception{
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("jianwei:mcp:grant:"+token)).thenReturn(json.writeValueAsString(new McpAuthorization.Grant(10,"customer","CUSTOMER",session.getId(),1)));
    session.setAttribute("accountVersion",1);
    var context=SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken("10","",List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    session.setAttribute("SPRING_SECURITY_CONTEXT",context);
    when(sessions.findById(session.getId())).thenReturn(session);
    when(accounts.get(10)).thenReturn(new AccountStateCache.State(true,1,new UserVO(10L,"13800000001","客户",List.of("CUSTOMER"))));
  }
  @Test void validGrantPasses(){assertEquals(10,auth.verify("Bearer "+token).userId());}
  @Test void missingGrantFails(){assertThrows(ResponseStatusException.class,()->auth.verify("Bearer fake"));}
  @Test void loggedOutSessionFails(){when(sessions.findById(session.getId())).thenReturn(null);assertThrows(ResponseStatusException.class,()->auth.verify("Bearer "+token));}
  @Test void changedRoleOrVersionFails(){when(accounts.get(10)).thenReturn(new AccountStateCache.State(true,2,new UserVO(10L,"13800000001","客户",List.of("CUSTOMER"))));assertThrows(ResponseStatusException.class,()->auth.verify("Bearer "+token));}
  @Test void principalCannotBeSwapped(){var c=SecurityContextHolder.createEmptyContext();c.setAuthentication(new UsernamePasswordAuthenticationToken("99","",List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));session.setAttribute("SPRING_SECURITY_CONTEXT",c);assertThrows(ResponseStatusException.class,()->auth.verify("Bearer "+token));}
}
