# Garbage First Collector in JikesRVM
## Roadmap
1. Parallel GC with small numbers of regions including Eden and Survivor.
2. Parallel GC with more regions including Eden, Survivor, and Old gen.
3. Concurrent?

## Implementation Plan
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

    We divide heap into regions of the same size. Each region will be accessed by the address where the region starts. An instance of G1Space uses a AddressArray to stores the start address of each region. 
    
    In other words, an instance of this class holds a global pool of regions, but this class do not directly allocate objects into regions.
    ```
    protected final AddressArray regions = AddressArray.create(number_of_regions);
    ```
    We can also use extra ```AddressArray```s to store the roles of each region.
    ```
    protected final AddressArray eden = AddressArray.create(number_of_regions);
    protected final AddressArray survivior = AddressArray.create(number_of_regions);
    protected final AddressArray old = AddressArray.create(number_of_regions);
    ```
    There also should be methods for the caller to access the eden region and the survivor region.
    ```
    public Address getEdenRegion(){}
    public Address getSurvivorRegion(){}
    ```
    The method ```traceObject()``` for tracing an object should be defined. In this method, while getting all referenced objects, it also copies and pastes current object to Survivior Space. Pasting is realized by calling ```alloc()``` method which is defined in G1Collector.
    ```
    public ObjectReference traceObject(){}
    ```
    A per-region basis ```releaseRegion()``` method should also be defined. This method will be called in ```release()``` to clear dead objects.
    ```
    public ObjectReference releaseRegion(){}
    ```


- G1SpaceLocal

    Each mutator thread creates an instance of this class which stores a list of bump pointers that stores the first address in a region where a new object can be allocated. This class should be aware of the size of each region which indicates the remaining space of each region.
    ```
    protected final ArrayList<BumpPointer> bumpPointers = new ArrayList<BumpPointer>();
    ```
    When the mutator tries to allocate an objects, it first visits every bump pointers in ```bumpPointers```, see if the object can be allocated by a bump pointers.
    

### Plan
- G1
    It creates an instance of G1Space, and defines the activities during GC.
    ```
  public static final G1Space g1 = new G1Space("g1", false, VMRequest.discontiguous());
  public void collectionPhase(short phaseId) {}
    ```

- G1Mutator

    It mainly creates an instance of G1SpaceLocal and defines ```alloc()``` method for allocating objects.
    ```
    protected G1SpaceLocal gl = new G1SpaceLocal(G1.g1space);
    public Address alloc(){}
    ```

- G1Collector

    This class difines the activities during a GC. In G1GC, 

- G1TraceLocal

- G1Constraints

    Based on previous implementation.

### Others
  - Change the object layout —— set up a counter for how many cycles of GC an object has survived.

## Links
JikesRVM
- https://github.com/JikesRVM/JikesRVM

Related papers
- http://users.cecs.anu.edu.au/~steveb/pubs/theses/zhao-2018.pdf
- https://users.cecs.anu.edu.au/~steveb/pubs/papers/g1-vee-2020.pdf

G1 Collector Tutorials
- https://www.oracle.com/technetwork/tutorials/tutorials-1876574.html
- https://www.redhat.com/en/blog/part-1-introduction-g1-garbage-collector