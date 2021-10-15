# Garbage First Collector in JikesRVM
## Roadmap
1. Parallel GC with regions including eden and survivor space.
2. Parallel GC with regions including eden, survivor, and old gen.
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

    This class implements a garbage first space that divides the heap into regions of the same size. Each region will be accessed by the address where the region starts. It allows the memory space to be discontiguous and allows the space to increase its size as the application demands.
    
    Constants
    ```java
    public static final int MAX_NUM_REGION = 2000;
    // TODO: public static final int REGIONS_SIZE
    public int num_current_regions;
    ```
    An instance of ```G1Space``` uses an ```AddressArray``` to store the start address of each region. In other words, an instance holds a global pool of regions, but this class does not directly allocate objects into regions.
    ```java
    protected final AddressArray regions = AddressArray.create(MAX_NUM_REGION);
    ```
    We can also use extra ```AddressArray``` to store the roles of each region.
    ```java
    protected final AddressArray eden = AddressArray.create(MAX_NUM_REGION);
    protected final AddressArray survivor = AddressArray.create(MAX_NUM_REGION);
    protected final AddressArray old = AddressArray.create(MAX_NUM_REGION);
    ```
    There are methods for the caller to access ```eden```, ```survivor```, and ```old``` regions. The methods below remove elements from ```AddressArray```. (This way avoids the race condition between mutator threads while allocating objects. However, it may decrease the utilization of regions which depends on the size of the region.)
    ```java
    public Address getEdenRegion(){}
    public Address getSurvivorRegion(){}
    public Address getOldRegion(){}
    ```
    If there is no avaliable region left in ```eden```, ```survivor```, or ```old```, and```num_current_regions``` does not exceed ```MAX_NUM_REGION```, the getter methods defined the previous should call ```expandRegions()``` to request virtual memory of size ```REGIONS_SIZE``` and return its address.
    ```java
    public Address expandRegions(){}
    ```
    
    A method that recycles regions from mutator threads should also be defined. Since after every cycle of GC, the regions held by the mutator thread become unallocated regions, the bump pointer that associates with the region becomes invalid, the mutator threads need to inform the global pool of which regions can be reused. This method also mutates ```eden```, ```survivor```, and ```old```.
    ```java
    public void returnRegion(Address region, int Type){}
    ```
    After each GC, the role of each should be recalculated based on its liveness, then updates ```eden```, ```survivor```, and ```old```.
    ```java
    public void updateRoles(){}
    ```
    The method ```traceObject()``` is for tracing an object. In this method, while getting all referenced objects, it also copies and pastes current object to current survivor regions. Pasting is realized by calling ```allocCopy()``` defined in G1Collector. This method also updates accounting information for updating roles of each region.
    We can refer to ```CopySpace.traceObject()``` for the implementation of this method.
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
    When the mutator tries to allocate an object, it first visits every bump pointer in ```bumpPointers```, see if any of the bump pointers can allocate the object. If it fails, ```alloc()``` will request a new eden region from the global pool ```g1.eden```, and assign a bump pointer to the region.
    ```java
    public Address alloc(int bytes, int align, int offset){}
    ```
    

### Plan
The interface between Policy and JVM.
- G1

    It creates an instance of G1Space, and defines the activities during GC.
    In the ```RELEASE``` phase, ```g1.release()``` calls  ```updateRoles()``` to update rolls of each region.
    ```java
    public static final G1Space g1 = new G1Space("g1", VMRequest.discontiguous());
    public final Trace g1Trace = new Trace(metaDataSpace);  // live objects tracer
    public void collectionPhase(short phaseId) {
        // ...
        if (phaseId == G1.RELEASE) {
            g1.release();
            super.collectionPhase(phaseId);
            return;
        }
        // ...
    }
    ```

- G1Mutator

    It creates an instance of G1SpaceLocal and defines ```alloc()``` method for allocating objects.
    ```java
    protected G1SpaceLocal gl = new G1SpaceLocal(G1.g1space);
    public Address alloc(int bytes, int align, int offset, int allocator){}
    ```
    At the end of a GC cycle, each mutator thread returns regions to the global pool. (We have to make sure this happens before the new roles are calculated so that we may reschedule this action to another phase.)
    ```java
    public void collectionPhase(short phaseId, boolean primary) {
        // ...
        if (phaseId == SS.RELEASE) {
          super.collectionPhase(phaseId, primary);
          gl.release();
          return;
        }
        // ...
    }
    ```
    

- G1Collector

    This class difines the activities during a GC. It also provides ```allocCopy()```method for pasting objects during GC. The ```collectionPhase``` here defines the trace behavior in each phase.
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