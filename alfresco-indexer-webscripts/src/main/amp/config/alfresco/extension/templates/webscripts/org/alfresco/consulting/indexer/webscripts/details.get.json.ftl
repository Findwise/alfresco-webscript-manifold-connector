<#escape x as jsonUtils.encodeJSONString(x)>
{
  "readableAuthorities" : [
    <#list readableAuthorities as readableAuthority>
      "${readableAuthority}"
      <#if readableAuthority_has_next>,</#if>
    </#list>
  ],
  "path" : "${path}",
  <#if shareUrlPath??>
    "shareUrlPath" : "${shareUrlPrefix + shareUrlPath}",
  </#if>
  <#if contentUrlPath??>
    "contentUrlPath" : "${contentUrlPrefix + contentUrlPath}",
  </#if>
  <#if thumbnailUrlPath??>
    "thumbnailUrlPath" : "${thumbnailUrlPrefix + thumbnailUrlPath}",
  </#if>
  <#if previewUrlPath??>
    "previewUrlPath" : "${previewUrlPrefix + previewUrlPath}",
  </#if>

  <#assign propNames = properties?keys>
  "aspects" : [
    <#list aspects as aspect>
    "${aspect}"
    <#if aspect_has_next>,</#if>
  </#list>
  ],
  "properties" : [
    <#list propNames as propName>
      {
        <#assign propPair=properties[propName] >
        "name" : "${propName}",
        "type" : "${propPair.first}",
        "value" : "${propPair.second}"
      }
      <#if propName_has_next>,</#if>
    </#list>
  ],
  "tags": [
    <#list tags as tag>
      "${tag}"<#if tag_has_next>,</#if>
    </#list>
  ],
  "comments": [
    <#list comments as comment>
    "${comment?replace("<(.|\n)*?>",  "", "r")}"<#if comment_has_next>,</#if>
    </#list>
  ],
  "likes": ${likes?c}
}
</#escape>