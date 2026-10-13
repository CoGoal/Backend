package edu.stankin.cogoalmain.pojo.dto;
import jakarta.validation.constraints.*;
import java.util.HashMap;
import java.util.Map;

/** Distinguishes an omitted avatar from explicit null; rejects account fields in main. */
public class ProfileUpdate {
    @Size(min=1,max=100) @Pattern(regexp=".*\\S.*") public String name;
    @Size(max=2048) public String avatarUrl;
    @com.fasterxml.jackson.annotation.JsonIgnore public boolean avatarPresent;
    @Size(max=0) private final Map<String,Object> unsupported=new HashMap<>();
    @com.fasterxml.jackson.annotation.JsonSetter("avatarUrl")
    public void setAvatarUrl(String value) { avatarUrl=value; avatarPresent=true; }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void unknown(String key,Object value) { unsupported.put(key,value); }
}
