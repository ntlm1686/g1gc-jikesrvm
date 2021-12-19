## Region-based memory management of JikesRVM

## Build

### Prerequisites
- JDK (>= 6 and < 9)
- Ant (>= 1.7 and < 1.10)
- GCC with multilibs
- Bison

After all the requirements are satisified, run the command inside the project folder
```
$ bin/buildit localhost development
```

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
