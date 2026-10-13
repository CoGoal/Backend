package edu.stankin.cogoalauth;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ApiContractTest {
    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(Object value) {return (Map<String,Object>) value;}
    private Map<String,Object> document;
    private Object resolve(String ref) {
        assertThat(ref).startsWith("#/");
        Object current=document;
        for(String part:ref.substring(2).split("/")) {
            current=map(current).get(part.replace("~1","/").replace("~0","~"));
            assertThat(current).as(ref).isNotNull();
        }
        return current;
    }
    private void references(Object value) {
        if(value instanceof Map<?,?> values) {
            if(values.containsKey("$ref")) resolve(values.get("$ref").toString());
            values.values().forEach(this::references);
        }else if(value instanceof List<?> values) values.forEach(this::references);
    }
    @Test void authContractUsesUnifiedLoginBigintAndSeparateProfileFieldsWithoutBrokenReferences() throws Exception {
        var options=new LoaderOptions();options.setAllowDuplicateKeys(false);
        Path file=Path.of(System.getProperty("basedir","."),"../docs/api/openapi.yaml");
        document=map(new Yaml(new SafeConstructor(options)).load(Files.readString(file)));
        references(document);
        var login=map(resolve("#/components/schemas/LoginRequest"));
        assertThat(login.get("required")).isEqualTo(List.of("login","password"));
        assertThat(map(login.get("properties"))).doesNotContainKey("email");
        assertThat(map(map(login.get("properties")).get("login"))).doesNotContainKey("format");
        var user=map(map(resolve("#/components/schemas/AuthUser")).get("properties"));
        assertThat(map(user.get("id"))).containsEntry("type","integer").containsEntry("format","int64");
        assertThat(user).doesNotContainKeys("name","avatarUrl");
        var profile=map(map(resolve("#/components/schemas/UpdateProfileRequest")).get("properties"));
        assertThat(profile).containsKeys("name","avatarUrl").doesNotContainKey("username");
        assertThat(map(resolve("#/paths/~1auth~1me~1username"))).containsKey("patch");
        assertThat(map(resolve("#/components/schemas/AuthResponse")).get("required"))
                .isEqualTo(List.of("accessToken","refreshToken","expiresIn","user"));
    }
}
