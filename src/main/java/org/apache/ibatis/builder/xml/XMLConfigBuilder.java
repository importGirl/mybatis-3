/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  /**
   * 是否已解析
   */
  private boolean parsed;
  /**
   * 基于 java xpath 解析器
   */
  private final XPathParser parser;
  /**
   * 环境
   */
  private String environment;
  /**
   * 默认 ReflectorFactory
   */
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  /**
   * 构造方法
   * @param parser
   * @param environment
   * @param props
   */
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 解析 XML 成Configuration 对象
   *
   * @return
   */
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    // 解析 xml configuration 节点配置
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 解析配置文件中的每个标签
   * @param root
   */
  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      // 解析 <properties/> 下的元素
      propertiesElement(root.evalNode("properties"));
      // 解析 <settings></settings> 下的配置
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 加载自定义的 vfs 类
      loadCustomVfs(settings);
      // 加载自定义的 log 实现类
      loadCustomLogImpl(settings);
      // 解析 <typeAliases></typeAliases> 标签
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析 <plugins></plugins> 标签
      pluginElement(root.evalNode("plugins"));
      // 解析 <ObjectFactory></ObjectFactory> 标签
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析 <objectWrapperFactory> 标签
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析 <reflectorFactory> 标签
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 设置 <settings> 值到 configuration 中
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析 <environment> 标签
      environmentsElement(root.evalNode("environments"));
      // 解析 <databaseIdProvider> 标签
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析 <typeHandlers> 标签
      typeHandlerElement(root.evalNode("typeHandlers"));
      // 解析 <mappers> 标签
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * 获得 <settings></settings> 下的配置
   *
   * <settings>
   *     <setting name="autoMappingBehavior" value="NONE"/>
   *     <setting name="autoMappingUnknownColumnBehavior" value="WARNING"/>
   *     <setting name="cacheEnabled" value="false"/>
   *     <setting name="proxyFactory" value="CGLIB"/>
   *     <setting name="lazyLoadingEnabled" value="true"/>
   *     <setting name="aggressiveLazyLoading" value="true"/>
   *     <setting name="multipleResultSetsEnabled" value="false"/>
   *     <setting name="useColumnLabel" value="false"/>
   *     <setting name="useGeneratedKeys" value="true"/>
   *     <setting name="defaultExecutorType" value="BATCH"/>
   *     <setting name="defaultStatementTimeout" value="10"/>
   *     <setting name="defaultFetchSize" value="100"/>
   *     <setting name="mapUnderscoreToCamelCase" value="true"/>
   *     <setting name="safeRowBoundsEnabled" value="true"/>
   *     <setting name="localCacheScope" value="STATEMENT"/>
   *     <setting name="jdbcTypeForNull" value="${jdbcTypeForNull}"/>
   *     <setting name="lazyLoadTriggerMethods" value="equals,clone,hashCode,toString,xxx"/>
   *     <setting name="safeResultHandlerEnabled" value="false"/>
   *     <setting name="defaultScriptingLanguage" value="org.apache.ibatis.scripting.defaults.RawLanguageDriver"/>
   *     <setting name="callSettersOnNulls" value="true"/>
   *     <setting name="logPrefix" value="mybatis_"/>
   *     <setting name="logImpl" value="SLF4J"/>
   *     <setting name="vfsImpl" value="org.apache.ibatis.io.JBoss6VFS"/>
   *     <setting name="configurationFactory" value="java.lang.String"/>
   *   </settings>
   *
   * @param context
   * @return
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    // 获得节点下的子属性
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 创建元类对象
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    // 循环遍历属性， 判断有的属性如果没有 set方法， 抛出异常
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  /**
   * 加载自定义的 vfs 实现类
   * @param props
   * @throws ClassNotFoundException
   */
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    // 获得 vfsImpl 属性
    String value = props.getProperty("vfsImpl");
    // 支持加载多个 vfs 实现类
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  /**
   * 加载自定义的 log 实现类
   * @param props
   */
  private void loadCustomLogImpl(Properties props) {
    // 通过属性值 获得 日志实现类； typeAliasRegistry 中有注册则直接返回， 在configuration 中有注册一些默认值
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    // 设置日志实现类
    configuration.setLogImpl(logImpl);
  }

  /**
   * 解析以下标签
   *  <typeAliases>
   *     <typeAlias alias="BlogAuthor" type="org.apache.ibatis.domain.blog.Author"/>
   *     <typeAlias type="org.apache.ibatis.domain.blog.Blog"/>
   *     <typeAlias type="org.apache.ibatis.domain.blog.Post"/>
   *     <package name="org.apache.ibatis.domain.jpetstore"/>
   *   </typeAliases>
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // 如果是 <package> 标签
        if ("package".equals(child.getName())) {
          // 获得标签 name 的值
          String typeAliasPackage = child.getStringAttribute("name");
          // 注册别名
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        }
        // 如果是其它标签
        else {
          // alias 属性
          String alias = child.getStringAttribute("alias");
          // type 属性
          String type = child.getStringAttribute("type");
          try {
            // 注册别名
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 解析 <plugins></plugins> 标签
   *
   * <plugins>
   *     <plugin interceptor="org.apache.ibatis.builder.ExamplePlugin">
   *       <property name="pluginProperty" value="100"/>
   *     </plugin>
   * </plugins>
   *
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // 获得 interceptor 属性
        String interceptor = child.getStringAttribute("interceptor");
        // 获得 子标签下的属性
        Properties properties = child.getChildrenAsProperties();
        // 反射创建 Interceptor 对象
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        // 把属性值设置到 插件对象 中
        interceptorInstance.setProperties(properties);
        // 添加插件到 configuration 中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 解析 <ObjectFactory></ObjectFactory> 标签
   *  <objectFactory type="org.apache.ibatis.builder.ExampleObjectFactory">
   *     <property name="objectFactoryProperty" value="100"/>
   *   </objectFactory>
   * @param context
   * @throws Exception
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获得标签中到 type 属性
      String type = context.getStringAttribute("type");
      // 获得标签下到 <properties></properties> 属性
      Properties properties = context.getChildrenAsProperties();
      // 创建对象
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      // 设置属性
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  /**
   * 解析 <objectWrapperFactory></objectWrapperFactory> 标签
   *
   *   <objectWrapperFactory type="org.apache.ibatis.builder.CustomObjectWrapperFactory" />
   *
   * @param context
   * @throws Exception
   */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  /**
   *  解析 <reflectorFactory></reflectorFactory> 标签
   *
   *   <reflectorFactory type="org.apache.ibatis.builder.CustomReflectorFactory"/>
   *
   * @param context
   * @throws Exception
   */
  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析指定节点下的元素
   *
   *  <properties resource="org/apache/ibatis/builder/jdbc.properties">
   *     <property name="prop1" value="aaaa"/>
   *     <property name="jdbcTypeForNull" value="NULL" />
   *   </properties>
   *
   * @param context
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      // 获得子标签中的 properties
      Properties defaults = context.getChildrenAsProperties();
      // 获得属性 resources
      String resource = context.getStringAttribute("resource");
      // 获得属性 url
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }

      // 加载对应的资源 resource / url
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      // 获得配置文件中的变量
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      // 重新赋值
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  /**
   * 设置 <settings></settings> 标签中的值到 Configuration 中
   * @param props
   */
  private void settingsElement(Properties props) {

    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));

    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 解析 <environments></environments> 标签
   *  <environments default="development">
   *     <environment id="development">
   *       <transactionManager type="JDBC">
   *         <property name="" value=""/>
   *       </transactionManager>
   *       <dataSource type="UNPOOLED">
   *         <property name="driver" value="${driver}"/>
   *         <property name="url" value="${url}"/>
   *         <property name="username" value="${username}"/>
   *         <property name="password" value="${password}"/>
   *       </dataSource>
   *     </environment>
   *   </environments>
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) {
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   * 解析 <databaseIdProvider></databaseIdProvider> 标签
   *
   *   <databaseIdProvider type="DB_VENDOR">
   *     <property name="Apache Derby" value="derby"/>
   *   </databaseIdProvider>
   *
   * @param context
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    // 获得 databaseId 都属性值
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  /**
   * 解析 <transactionManager></transactionManager> 标签
   * @param context
   * @return
   * @throws Exception
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  /**
   * 解析 <datasources></datasources> 标签
   * @param context
   * @return
   * @throws Exception
   */
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 解析 <typeHandlers> </typeHandlers> 标签, 并注册
   *
   *   <typeHandlers>
   *     <typeHandler javaType="String" handler="org.apache.ibatis.builder.CustomStringTypeHandler"/>
   *     <typeHandler javaType="String" jdbcType="VARCHAR" handler="org.apache.ibatis.builder.CustomStringTypeHandler"/>
   *     <typeHandler handler="org.apache.ibatis.builder.CustomLongTypeHandler"/>
   *     <package name="org.apache.ibatis.builder.typehandler"/>
   *   </typeHandlers>
   * @param parent
   */
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 解析 <mapper></mapper> 标签
   *
   *   <mappers>
   *     <mapper resource="org/apache/ibatis/builder/xsd/BlogMapper.xml"/>
   *     <mapper url="file:./src/test/java/org/apache/ibatis/builder/xsd/NestedBlogMapper.xml"/>
   *     <mapper class="org.apache.ibatis.builder.xsd.CachedAuthorMapper"/>
   *     <package name="org.apache.ibatis.builder.mapper"/>
   *   </mappers>
   *
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
