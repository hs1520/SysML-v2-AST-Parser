# SysML-v2-AST-Parser
任务描述：实现 SysML v2 AST 解析与 Docker 一键部署 你需要为这个 MBSE 原型项目实现一套基于官方 SysML v2 AST/API 的解析与转换能力，并支持 Docker 一键部署。当前 Python 侧的 DesignAgent._populate_model_from_sysml() 使用正则解析，精度很低；现在要改成由 Java 侧负责 SysML v2 AST 解析，再导出结构化结果供 Python 侧映射成 SysMLModel。
