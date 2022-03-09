/**
 * 2022.2.25
 * 对线程集合进行测试 
 */

#include<pthread.h>

int g = 0;
int d = 0;
int c = 0;
int x;

pthread_t t1;
pthread_t t2;
pthread_t t3;
pthread_t t4;

void *thread3(void*);
void *thread4(void*);

void *thread1(void *arg) {
	int a = 4;
	// x = 5;
	d = 1;

	pthread_create(&t3, NULL, thread3, NULL);

	g = 1;
	c = 1;
}

void *thread2(void *arg) {
	int b = 5;
	x = 11;
	if (d == 1) {
		g = 2;
	}
}

void *thread3(void *arg) {
	pthread_create(&t4, NULL, thread4, NULL);
}

void *thread4(void *arg) {
	x = 12;			// usagePoint: "WRITE:[t4:{t1=CREATED_THREAD,t3=CREATED_THREAD,t4=CREATED_THREAD},[]]"
}

void main(){

	g = 0;
	
	pthread_create(&t1, NULL, thread1, NULL);

	d = 3;
	c = 2;
	x = 10;

	pthread_create(&t2, NULL, thread2, NULL);

	return;
}

/**
 * 线程集合是在当前线程下可见的线程的集合，不包括main线程
 * 若当前处于主线程main中，则其他已产生的线程均为PARENT_THREAD
 * 若当前处于非主线程中，则已产生的所有非主线程中：如果不是当前线程所创建的线程，则为CREATED_THREAD，如果是当前线程所创建的线程，则为PARENT_THREAD
 */