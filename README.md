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
Please refer to Userguide Chapter 11 The MMTk Test Harness.
