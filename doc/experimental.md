# Experimental

This document describes some high-level experimental features that aim to enable more aggressive optimization.

## Enable access modification of kept items

Access modification is important for optimization, but may be prohibited by broad keep rules. Currently keep rule authors must explicitly allow access modification using the `allowaccessmodification` modifier.

It is possible to limit the negative impact of broad keep rules on optimization by changing keep rules to allow access modification by default.

If the app is sensitive to whether a given class, field, or method is non-public or not, it should explicitly disallow access modification using the `disallowaccessmodification` modifier:

```
-keep,disallowaccessmodification class com.example.** { <fields>; }
```

Enable this feature:

```
-Dcom.android.tools.r8.allowAccessModificationInKeepRule=1
```

## Enable final modification of kept instance fields

The ability to unset the final flag of fields is important for the ability to inline constructors that assign final fields.

Final modification is currently tied to optimization, i.e., if a given rule prohibits optimization of a field (e.g., since the field is accessed using reflection) then the field's final flag cannot be modified by R8.

It is possible to enable more constructor inlining by decoupling final modification from optimization and changing keep rules to allow final modification by default.

If the app is sensitive to whether a given instance field is final or not, it should explicitly disallow final modification using the `disallowfinalmodification` modifier.

```
-keep,disallowfinalmodification class com.example.** { <fields>; }
```

Enable this feature:

```
-Dcom.android.tools.r8.allowFinalModificationInKeepRule=1
-Dcom.android.tools.r8.decoupleFinalModificationFromOptimization=1
```

## Enable code analysis of kept methods

When a method is kept, R8 will propagate any information about the behavior of the method to callers (e.g., the fact that the method always returns a non-null value). This is often overly conservative, since most kept methods will not have their implementation changed.

It is possible to change this behavior so that keep rules authors explicitly need to state that a given method may have its implementation changed using the `allowcodereplacement` modifier.

```
-keep,allowcodereplacement class com.example.** { <methods>; }
```

Enable this feature:

```
-Dcom.android.tools.r8.allowCodeReplacementInKeepRule=0
-Dcom.android.tools.r8.decoupleCodeReplacementFromOptimization=1
```
