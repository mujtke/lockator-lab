/**
 * 2022.02.25 
 * 测试falseUnsafe什么时候出现，能否产生新的谓词
 */

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
	x = 10;

	pthread_create(&t2, NULL, thread2, NULL);

	return;
}

/**
 * 检测到了falseUnsafe
 * t1中的g=1与t2中的g=2
 * 产生了新谓词，进行了一次细化
 * 造成假阳性的原因在于线程t2执行时，d=3，即g=2是不会执行的
 * /