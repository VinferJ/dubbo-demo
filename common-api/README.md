

- 公共接口模块，开发中在consumer模块中中作为静态调用，没有实现
- 该模块中所有接口真正的实现都在provider模块中
- 该模块所提供的api是在consumer模块业务方法中的依赖的api
- 整合springboot后，被引用的接口通过添加@Reference注解进行远程引用