#include<pthread.h>

int g = 0;
int d = 0;
int c = 0;
int x;

pthread_t t1;
pthread_t t2;
pthread_t t_3;

void *thread1(void *arg) {
	int a = 4;
	// x = 5;
	d = 1;
	g = 1;
	c = 1;
}

void *thread2(void *arg) {
	int b = 5;
	g = 4;
	x = 11;
	if (d == 1) {
		g = 2;
	}
}

void *thread3(void *arg) {
	return;
}

void main(){

	g = 0;
	
	pthread_create(&t1, NULL, thread1, NULL);

	d = 3;
	c = 2;
	g = 1;
	x = 10;

	pthread_create(&t2, NULL, thread2, NULL);

	x = 12;		// usagePoint:"WRITE:[main:{t1=PARENT_THREAD, t2=PARENT_THREAD},[]]"

	pthread_create(&t_3, NULL, thread3, NULL);

	x = 15;		// usagePoint: "WRITE:[main:{t1=PARENT_THREAD, t2=PARENT_THREAD, t_3=PARENT_THREAD},[]]"

	return;
}


覆盖情况：
main线程中的"x=15"的usagePoint会覆盖main线程中的"x=12"对应的usagePoint