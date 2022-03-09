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

void main(){

	g = 0;
	
	pthread_create(&t1, NULL, thread1, NULL);

	d = 3;
	c = 2;
	g = 1;
	x = 10;

	pthread_create(&t2, NULL, thread2, NULL);

	return;
}