
int sum(int m, int n) {
	if (m == 0)
		return n;
	else 
	{
		int tmp = sum(m-1, n+1);
		return tmp;
	}
}

void main(void){

	int a = 4;
	int b = 3;
	int result = sum(a, b);

	return;
}