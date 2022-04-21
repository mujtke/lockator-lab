/**
 * 加锁，测试同步之后线程交替对与条件分支的影响
 */ 


#include<pthread.h>

int b = 0, c = 3;

pthread_t t1, t2;
pthread_mutex_t l1;

void *thread1(void *arg) {
	int a = 0;
	
	pthread_mutex_lock(&l1);
	if (c == 4) {
		pthread_mutex_unlock(&l1);
		b = 7;
	}
}

void *thread2(void *arg) {
	pthread_mutex_lock(&l1);
	c++;
	pthread_mutex_unlock(&l1);
	b = 8;
}

void main() {

	pthread_create(&t2, NULL, thread2, NULL);

	pthread_create(&t1, NULL, thread1, NULL);

	return;
}