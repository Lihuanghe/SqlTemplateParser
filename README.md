
## 一个简单的sql参数替换引擎，支持动态参数名，动态表名.
## 主要用在自动报表工具（引擎）上。可以支持sql设置参数

# 用法: 


1. 表名，参数名拼接

> \#{pName#{pName}} ： 参数pName值为5,首先解析成#{pName5}，然后再取pName5的值替换。比如用于动态表名： select * from table_#{month} where id = ${id}.

2. 参数替换

> ${parameter} ： 替换为 ? ,同时设置对应位置的参数对象，供prepareStatement使用. 如果参数为空，则不替换。参数目前只支持String类型

> ${parameter#{pName}} : 如果参数pName值为5,引擎首先解析成${parameter5},然后再取值。
如果有一个参数parameter5值为"hello world"，则替换为?,且对应位置的参数值设置为"hello world"

> @{arraysParameter} : 数组参数替换。如果数据不为空，且长度大于0 ，则替换为?,?,? .用于 sql的in , not in 语句，如： where id in ( 'Nouse',@{Ids} ) .

> $[optParam: statment ] : 支持可选语句，如果参数optParam，值不为空，则解析[]内的statment,否则移除整条statment。用于传入可选参数：

```sql
select * from shops 
where 1=1 
$[shopName: 
and  shop_name = ${shopName} 
] 
and status = 1 
```

> @[optArrays: statment ] : 与$[]含义相同，但参数为数组，或者集合类型。仅当optArrays不为空，且长度大于0才执行statment。

```sql
select * from shops 
where 1=1 
@[Ids: 
and id in ( 'Nouse',@{Ids} ) 
and beginDate = ${datetime}
 ] 
and status = 1
```

3. 目前正在做。

> 参数支持调用js进行处理，以'|'分开，前边是参数名，后边是js代码。 js代码可选。 如：

```
${abc|DateFormat.format(abc,'yyyy-MM-dd')} 

@{b3|['hello'].concat(b3.join('*'))}

```
