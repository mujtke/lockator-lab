#include<pthread.h>

int g = 0;
int d = 0;
int c = 0;
int x;

pthread_t t1;
pthread_t t2;

void *thread1(void *arg) {
	int a = 4;
	// x = 5;
	d = 1;			// usagePoint: "WRITE:[t1:{t1=CREATED_THREAD},[]]"
	g = 1;			// usagePoint: "WRITE:[t1:{t1=CREATED_THREAD},[]]"
	c = 1;			// usagePoint: "WRITE:[t1:{t1=CREATED_THREAD},[]]"
}

void *thread2(void *arg) {
	int b = 5;
	g = 4;			// usagePoint: "WRITE:[t2:{t1=PARENT_THREAD, t2=CREATED_THREAD},[]]"
	x = 11;			// usagePoint: "WRITE:[t2:{t1=PARENT_THREAD, t2=CREATED_THREAD},[]]"
	if (d == 1) {	// usagePoint: "READ:[t2:{t1=PARENT_THREAD, t2=CREATED_THREAD},[]]"
		g = 2;		// usagePoint: "WRITE:[t2:{t1=PARENT_THREAD, t2=CREATED_THREAD},[]]"
	}
}

void main(){

	g = 0;			// usagePoint: "WRITE:[main:{},[]]"
	
	pthread_create(&t1, NULL, thread1, NULL);

	d = 3;			// usagePoint: "WRITE:[main:{t1=PARENT_THREAD},[]]"
	c = 2;			// usagePoint: "WRITE:[main:{t1=PARENT_THREAD},[]]"
	g = 1;			// usagePoint: "WRITE:[main:{t1=PARENT_THREAD},[]]"
	x = 10;			// usagePoint: "WRITE:[main:{t1=PARENT_THREAD},[]]"

	pthread_create(&t2, NULL, thread2, NULL);

	return;
}

覆盖的情况：
main线程中的"x=10"对应的usagePoint被t2线程中的"x=11"对应的usagePoint所覆盖
threadState的覆盖："x=11"对应的usagePoint中，线程集合为{t1=PARENT_THREAD, t2=CREATED_THREAD}，包含了"x=10"对应的线程集合{t1=PARENT_THREAD}
LockState的覆盖："x=11"对应的锁集合为"[]"，覆盖了同样为空集的"x=10"对应的锁集合"[]"

t1线程中,"x=5"对应的usagePoint为"WRITE:[t1:{t1=CREATED_THREAD},[]]"和main线程中的"x=10"没有形成覆盖，因为threadState没有形成覆盖关系