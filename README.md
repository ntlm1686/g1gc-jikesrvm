# g1gc-jikesrvm
## Young Gen GC (modified from semispace GC)
On this branch, I implemented a simple young generation GC
which composed of Eden space and From/To Space. All new objects
are allocating to the Eden Space. During the GC period, live
objects in Eden and From space are copied to To space, then
all dead objects in Eden and From space are wiped.
### build
```
$ bin/buildit localhost BaseBaseYoungGen
```
### run
```
$ dist/BaseBaseYoungGen_x86_64-linux/rvm HelloWorld
Hello, World!
```
### test
Please refer to [Userguide Chapter 11 The MMTk Test Harness](https://www.jikesrvm.org/UserGuide/TheMMTkTestHarness/index.html#x13-14200011.4).
```
# Example of standalone test
$ java -jar target/mmtk/mmtk-harness.jar MMTk/harness/test-scripts/Alignment.script plan=YG
```
![image](https://user-images.githubusercontent.com/78901505/137200218-57f9f166-14e0-439b-ac17-c684216b7893.png)
