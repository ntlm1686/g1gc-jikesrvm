## Region-based memory management of JikesRVM
This repository contains a copy of Jikes RVM patched with an implement of the [region-based garbage collection algorithm](https://archive.ph/20121020025006/http://www3.interscience.wiley.com/journal/113446436/abstract?CRETRY=1&SRETRY=0).

The implementation includes:
- parallel marking
- updating collection set
- parallel evacuation

We provide a ```RegionSpace```, the file is under ```MMTk/src/org/mmtk/policy```, which defines the policy of managing a heap as mutiple regions. To allocate objects to this region, we provide a modified version of ```BumpPointer```. The space can be extended to a garbage first GC or any other region-based like GCs.

[MMTk](https://github.com/mmtk/mmtk-jikesrvm) provides us the bindings to manage the memory in JikesRVM.

The official Jikes RVM website and repository can be found [here](https://www.jikesrvm.org/).

## Build

### Prerequisites
- JDK (>= 6 and < 9)
- Ant (>= 1.7 and < 1.10)
- GCC with multilibs
- Bison

After all the requirements are satisified, run the command at root folder of this project.
```
$ bin/buildit localhost development
```
RVM will be in the dist directory.

### Use docker
See [dockerfile](https://github.com/ljjsalt/jikesrvm-dev-env)

## Usage
You can use the compiled virtual machine to run java programs, if you are using a x86 64bit linux machine, you can run the command below
```
$ dist/development_x86_64-linux/rvm HelloWorld
```

If you see the unsuppoted class file version error, you have to specify the version of class files for VM.
```
$ javac -source 1.6 -target 1.6 HelloWorld.java
```

## License
EPL
