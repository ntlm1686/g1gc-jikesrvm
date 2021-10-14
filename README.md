# Garbage First Collector in JikesRVM
## Roadmap
1. Parallel GC with small numbers of regions including Eden and Survivor.
2. Parallel GC with more regions including Eden, Survivor, and Old gen.
3. Concurrent?

## Implementation Plan
### File Strucure
```
MMTk/src/org/mmtk
├── plan
│   └── g1
│       ├── G1.java                     # how to manage the heap(space)​, define GC phases
│       ├── G1Mutator.java              # how to allocate object to the heap (per mutator thread)
│       ├── G1Collector.java            # what happens in a garbage collection​ (per GC thread)
│       ├── G1TraceLocal.java           # How to trace live objects in heap​
│       └── G1Constraints.java          # configuration information for vm​
└── policy
    └── G1Space.java                    # how we define the structure of the heap
    └── G1SpaceLocal.java               # how a mutator thread allocate objects into the space
```

### Policy
Describe how a range of virtual address space is managed. 
- G1Space

    We divide heap into regions of the same size. Each region will be accessed by the address where the region starts. An instance of ```G1Space``` uses a ```AddressArray``` to stores the start address of each region. 
    
    In other words, an instance of this class holds a global pool of regions, but this class do not directly allocate objects into regions.
    ```java
    protected final AddressArray regions = AddressArray.create(number_of_regions);
    ```
    We can also use extra ```AddressArray```s to store the roles of each region.
    ```java
    protected final AddressArray eden = AddressArray.create(number_of_regions);
    protected final AddressArray survivor = AddressArray.create(number_of_regions);
    protected final AddressArray old = AddressArray.create(number_of_regions);
    ```
    There also should be methods for the caller to access the ```eden```, ```survivor```, and ```old```. These methods may remove elements from ```AddressArray```.
    ```java
    public Address getEdenRegion(){}
    public Address getSurvivorRegion(){}
    public Address getOldRegion(){}
    ```
    A method that retrieves regions from mutator thread should also be defined. Since after a cycle of GC, the regions that held by the mutator thread become unallocated regions, the bump pointer that associates with the region becomues invalid. This method also mutates ```AddressArray```.
    ```java
    public void returnRegion(Address region){}
    ```
    After each GC, the role of each should be recalculated based on tis liveness and updates ```eden```, ```survivor```, and ```old```.
    ```java
    public void updateRoles(){}
    ```
    The method ```traceObject()``` for tracing an object should be defined. In this method, while getting all referenced objects, it also copies and pastes current object to Survivior Space. Pasting is realized by calling ```alloc()``` defined in G1Collector. This method also updates accounting information for updating roles of each region.
    ```java
    public ObjectReference traceObject(ObjectReference obj){}
    ```
    A per-region basis ```releaseRegion()``` method should also be defined. This method will be used to release an eden region, and will be called in ```release()``` to release all eden regions.
    ```java
    public void releaseRegion(Address region){}
    public void release(){}
    ```


- G1SpaceLocal

    Each mutator thread creates an instance of this class which stores a list of bump pointers with the first address in each region where a new object can be allocated. This class should be aware of the size of each region which indicates the remaining space of each region.
    ```java
    protected final ArrayList<BumpPointer> bumpPointers = new ArrayList<BumpPointer>();
    ```
    This class will be bond to an instace of ```G1Space```.
    ```java
    public G1SpaceLocal(G1Space space) {this.space=space;}
    ```
    When the mutator tries to allocate an objects, it first visits every bump pointer in ```bumpPointers```, see if the object can be allocated by any of the bump pointers. If it fails, ```alloc()``` will request a new region from the global pool ```g1.eden```, and assign a bump pointer for the region.
    ```java
    public Address alloc(int bytes, int align, int offset){}
    ```
    

### Plan
Interface between Policy and JVM.
- G1

    It creates an instance of G1Space, and defines the activities during GC.
    ```java
    public static final G1Space g1 = new G1Space("g1", false, VMRequest.discontiguous());
    public void collectionPhase(short phaseId) {}
    ```

- G1Mutator

    It creates an instance of G1SpaceLocal and defines ```alloc()``` method for allocating objects.
    ```java
    protected G1SpaceLocal gl = new G1SpaceLocal(G1.g1space);
    public Address alloc(int bytes, int align, int offset, int allocator){}
    ```

- G1Collector

    This class difines the activities during a GC. It also provides ```allocCopy()```method for pasting objects during GC.
    ```java
    protected G1SpaceLocal gl = new G1SpaceLocal(G1.g1space);
    public G1Collector() {this(new G1TraceLocal(global().g1Trace));}

    public Address allocCopy(int bytes, int align, int offset, int allocator){}
    public void collectionPhase(short phaseId) {}
    ```

- G1TraceLocal

    This class extends ```TraceLocal```, which implements the core functionality for a transitive closure over the heap graph.

- G1Constraints

    Based on previous implementation.

### Others

  - Change the object layout —— set up a counter for how many cycles of GC an object has survived.
  - A modified ```BumpPointer```.
  - ...

## Links
JikesRVM
- https://github.com/JikesRVM/JikesRVM

Developing Environment
- https://github.com/ljjsalt/jikesrvm-dev-env

Related papers
- http://users.cecs.anu.edu.au/~steveb/pubs/theses/zhao-2018.pdf
- https://users.cecs.anu.edu.au/~steveb/pubs/papers/g1-vee-2020.pdf

G1 Collector Tutorials
- https://www.oracle.com/technetwork/tutorials/tutorials-1876574.html
- https://www.redhat.com/en/blog/part-1-introduction-g1-garbage-collector